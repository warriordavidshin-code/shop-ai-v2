package com.petitcamel.shop.reco.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Behaviour tracking for recommendations. Bots (by user agent) are never recorded, a product view counts once
 * per viewer per 30 minutes, and every served list gets an HMAC-signed request id so impressions, clicks and
 * cart adds can be tied to the list without trusting the client. Each event is stored at most once
 * (unique {@code dedupe_key}).
 */
@Service
public class RecoTrackingService {

    public static final Set<String> PLACEMENTS = Set.of("HOME_PERSONAL", "HOME_POPULAR", "HOME_NEW",
            "DETAIL_SIMILAR", "DETAIL_COORDI", "DETAIL_PRICE", "CART_MATCH", "MYPAGE_TASTE", "MYPAGE_RECENT");
    private static final Set<String> CLIENT_EVENTS = Set.of("IMPRESSION", "CLICK", "CART");
    private static final Pattern BOT = Pattern.compile(
            "bot|crawl|spider|slurp|headless|lighthouse|curl|wget|python|java/|httpclient|okhttp|axios|node-fetch"
                    + "|go-http|scrapy|facebookexternalhit|preview|monitor|uptime", Pattern.CASE_INSENSITIVE);
    private static final Duration VIEW_DEDUPE = Duration.ofMinutes(30);
    private static final int MAX_VIEWS_PER_HOUR = 120;
    private static final Duration REQUEST_ID_MAX_AGE = Duration.ofDays(2);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final byte[] signingKey;
    private final SecureRandom random = new SecureRandom();

    public RecoTrackingService(JdbcTemplate jdbc, Clock clock,
                               @Value("${app.reco.signing-key:${app.jwt.secret}}") String signingKey) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.signingKey = sha256(("reco-request-id|" + signingKey).getBytes(StandardCharsets.UTF_8));
    }

    public record EventRequest(String requestId, String eventType, Long productId, Integer itemCount) {
    }

    public record ParsedId(String placement, String variant, Instant issuedAt) {
    }

    public static boolean isBot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find();
    }

    // ------------------------------------------------------------------ request ids

    /** {@code v1.<placement>.<variant>.<epochSeconds base36>.<random>.<signature>} (under 80 chars). */
    public String issueRequestId(String placement, String variant) {
        byte[] nonce = new byte[6];
        random.nextBytes(nonce);
        String body = "v1." + placement + "." + variant + "." + Long.toString(clock.instant().getEpochSecond(), 36)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return body + "." + sign(body);
    }

    public ParsedId verify(String requestId) {
        if (requestId == null || requestId.length() > 80) {
            return null;
        }
        int lastDot = requestId.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        String body = requestId.substring(0, lastDot);
        String signature = requestId.substring(lastDot + 1);
        if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            return null;
        }
        String[] parts = body.split("\\.");
        if (parts.length != 5 || !"v1".equals(parts[0]) || !PLACEMENTS.contains(parts[1])) {
            return null;
        }
        Instant issued;
        try {
            issued = Instant.ofEpochSecond(Long.parseLong(parts[3], 36));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (issued.isBefore(clock.instant().minus(REQUEST_ID_MAX_AGE))) {
            return null;
        }
        return new ParsedId(parts[1], parts[2], issued);
    }

    /** A/B structure: every viewer is in variant A until an experiment is configured. */
    public String variantFor(Viewer viewer) {
        return "A";
    }

    // ------------------------------------------------------------------ events

    /** Returns true when stored, false when ignored (bot, invalid/expired id, duplicate). */
    public boolean recordEvent(Viewer viewer, EventRequest request, String userAgent) {
        if (isBot(userAgent) || request == null || request.eventType() == null) {
            return false;
        }
        String type = request.eventType().trim().toUpperCase();
        if (!CLIENT_EVENTS.contains(type)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 이벤트입니다.");
        }
        ParsedId id = verify(request.requestId());
        if (id == null) {
            return false;
        }
        Long productId = request.productId();
        if (!"IMPRESSION".equals(type)) {
            if (productId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "상품 정보가 필요합니다.");
            }
            Integer exists = jdbc.queryForObject("select count(*) from product where product_id = ?", Integer.class,
                    productId);
            if (exists == null || exists == 0) {
                return false;
            }
        } else {
            productId = null;
        }
        String dedupe = switch (type) {
            case "IMPRESSION" -> "imp:" + request.requestId();
            case "CLICK" -> "clk:" + request.requestId() + ":" + productId;
            default -> "cart:" + request.requestId() + ":" + productId;
        };
        Integer count = request.itemCount() == null ? null : Math.max(0, Math.min(request.itemCount(), 100));
        Long memberId = viewer.member() && viewer.personalizationAllowed() ? viewer.memberId() : null;
        int inserted = jdbc.update("""
                insert into recommendation_event (product_id, event_type, created_at, request_id, placement, variant,
                                                  member_id, session_key, item_count, dedupe_key)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (dedupe_key) where dedupe_key is not null do nothing""",
                productId, type, Timestamp.from(clock.instant()), request.requestId(), id.placement(), id.variant(),
                memberId, viewer.sessionKey(), count, dedupe.length() > 160 ? dedupe.substring(0, 160) : dedupe);
        return inserted == 1;
    }

    // ------------------------------------------------------------------ views / searches

    public boolean recordView(Viewer viewer, Long productId, String userAgent) {
        if (isBot(userAgent) || productId == null) {
            return false;
        }
        Long memberId = viewer.member() && viewer.personalizationAllowed() ? viewer.memberId() : null;
        String session = viewer.sessionKey();
        if (memberId == null && session == null) {
            return false;
        }
        Integer exists = jdbc.queryForObject("select count(*) from product where product_id = ?", Integer.class,
                productId);
        if (exists == null || exists == 0) {
            return false;
        }
        Instant now = clock.instant();
        String viewerClause = memberId != null ? "member_id = ?" : "session_key = ?";
        Object viewerValue = memberId != null ? memberId : session;
        Integer recent = jdbc.queryForObject("select count(*) from product_view_history where " + viewerClause
                        + " and product_id = ? and viewed_at >= ?", Integer.class, viewerValue, productId,
                Timestamp.from(now.minus(VIEW_DEDUPE)));
        if (recent != null && recent > 0) {
            return false;
        }
        Integer lastHour = jdbc.queryForObject("select count(*) from product_view_history where " + viewerClause
                + " and viewed_at >= ?", Integer.class, viewerValue, Timestamp.from(now.minus(Duration.ofHours(1))));
        if (lastHour != null && lastHour >= MAX_VIEWS_PER_HOUR) {
            return false;
        }
        jdbc.update("insert into product_view_history (product_id, member_id, session_key, viewed_at) values (?, ?, ?, ?)",
                productId, memberId, session, Timestamp.from(now));
        return true;
    }

    public boolean recordSearch(Viewer viewer, String keyword, String userAgent) {
        if (isBot(userAgent) || keyword == null) {
            return false;
        }
        String value = keyword.trim().replaceAll("\\s+", " ");
        if (value.length() < 2) {
            return false;
        }
        if (value.length() > 100) {
            value = value.substring(0, 100);
        }
        Long memberId = viewer.member() && viewer.personalizationAllowed() ? viewer.memberId() : null;
        if (viewer.member() && !viewer.personalizationAllowed()) {
            return false;
        }
        if (memberId == null && viewer.sessionKey() == null) {
            return false;
        }
        jdbc.update("insert into customer_search_history (member_id, session_key, keyword, searched_at) values (?, ?, ?, ?)",
                memberId, viewer.sessionKey(), value, Timestamp.from(clock.instant()));
        return true;
    }

    /** Behaviour history is only kept for 180 days. */
    @Scheduled(cron = "${app.reco.purge-cron:0 40 4 * * *}")
    public void purgeOldHistory() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(Duration.ofDays(180)));
        jdbc.update("delete from product_view_history where viewed_at < ?", cutoff);
        jdbc.update("delete from customer_search_history where searched_at < ?", cutoff);
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
