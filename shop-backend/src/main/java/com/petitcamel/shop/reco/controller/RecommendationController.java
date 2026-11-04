package com.petitcamel.shop.reco.controller;

import com.petitcamel.shop.reco.service.RecoTrackingService;
import com.petitcamel.shop.reco.service.RecommendationService;
import com.petitcamel.shop.reco.service.Viewer;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 고객용 추천 API. 요청자 본인의 로그인 정보 또는 비회원 쿠키(rec_sid)만 사용하며, 다른 회원 id를 받는
 * 파라미터는 없습니다.
 */
@RestController
public class RecommendationController {

    static final String SESSION_COOKIE = "rec_sid";
    private static final Pattern SESSION_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    private final RecommendationService recommendations;
    private final RecoTrackingService tracking;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public RecommendationController(RecommendationService recommendations, RecoTrackingService tracking,
                                    JdbcTemplate jdbc) {
        this.recommendations = recommendations;
        this.tracking = tracking;
        this.jdbc = jdbc;
    }

    public record ViewRequest(Long productId) {
    }

    public record SearchRequest(String keyword) {
    }

    public record PersonalizationRequest(boolean enabled) {
    }

    @GetMapping("/api/recommendations/personalized")
    public RecommendationService.RecoList personalized(@AuthenticationPrincipal MemberPrincipal principal,
                                                       @RequestParam(defaultValue = "HOME_PERSONAL") String placement,
                                                       @RequestParam(required = false) Integer limit,
                                                       HttpServletRequest request, HttpServletResponse response) {
        String safePlacement = "MYPAGE_TASTE".equals(placement) ? "MYPAGE_TASTE" : "HOME_PERSONAL";
        return recommendations.personalized(viewer(principal, request, response), safePlacement, limit);
    }

    @GetMapping("/api/recommendations/similar/{productId}")
    public RecommendationService.RecoList similar(@AuthenticationPrincipal MemberPrincipal principal,
                                                  @PathVariable Long productId,
                                                  @RequestParam(required = false) Integer limit,
                                                  HttpServletRequest request, HttpServletResponse response) {
        return recommendations.similar(viewer(principal, request, response), productId, limit);
    }

    @GetMapping("/api/recommendations/coordination/{productId}")
    public RecommendationService.RecoList coordination(@AuthenticationPrincipal MemberPrincipal principal,
                                                       @PathVariable Long productId,
                                                       @RequestParam(required = false) Integer limit,
                                                       HttpServletRequest request, HttpServletResponse response) {
        return recommendations.coordination(viewer(principal, request, response), productId, limit);
    }

    @GetMapping("/api/recommendations/similar-price/{productId}")
    public RecommendationService.RecoList similarPrice(@AuthenticationPrincipal MemberPrincipal principal,
                                                       @PathVariable Long productId,
                                                       @RequestParam(required = false) Integer limit,
                                                       HttpServletRequest request, HttpServletResponse response) {
        return recommendations.similarPrice(viewer(principal, request, response), productId, limit);
    }

    @GetMapping("/api/recommendations/popular")
    public RecommendationService.RecoList popular(@AuthenticationPrincipal MemberPrincipal principal,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(required = false) Integer period,
                                                  @RequestParam(required = false) Integer limit,
                                                  HttpServletRequest request, HttpServletResponse response) {
        return recommendations.popular(viewer(principal, request, response), type, period, limit);
    }

    @GetMapping("/api/recommendations/new-arrivals")
    public RecommendationService.RecoList newArrivals(@AuthenticationPrincipal MemberPrincipal principal,
                                                      @RequestParam(required = false) Integer limit,
                                                      HttpServletRequest request, HttpServletResponse response) {
        return recommendations.newArrivals(viewer(principal, request, response), limit);
    }

    @GetMapping("/api/recommendations/cart")
    public RecommendationService.RecoList cart(@AuthenticationPrincipal MemberPrincipal principal,
                                               @RequestParam(required = false) Integer limit,
                                               HttpServletRequest request, HttpServletResponse response) {
        return recommendations.cart(viewer(principal, request, response), limit);
    }

    @GetMapping("/api/recommendations/recent-similar")
    public RecommendationService.RecoList recentSimilar(@AuthenticationPrincipal MemberPrincipal principal,
                                                        @RequestParam(required = false) Integer limit,
                                                        HttpServletRequest request, HttpServletResponse response) {
        return recommendations.recentSimilar(viewer(principal, request, response), limit);
    }

    @PostMapping("/api/recommendations/events")
    public Map<String, Boolean> event(@AuthenticationPrincipal MemberPrincipal principal,
                                      @RequestBody RecoTrackingService.EventRequest body,
                                      @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                      HttpServletRequest request, HttpServletResponse response) {
        return Map.of("recorded", tracking.recordEvent(viewer(principal, request, response), body, userAgent));
    }

    @PostMapping("/api/recommendations/track/view")
    public Map<String, Boolean> view(@AuthenticationPrincipal MemberPrincipal principal,
                                     @RequestBody ViewRequest body,
                                     @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                     HttpServletRequest request, HttpServletResponse response) {
        return Map.of("recorded", tracking.recordView(viewer(principal, request, response),
                body == null ? null : body.productId(), userAgent));
    }

    @PostMapping("/api/recommendations/track/search")
    public Map<String, Boolean> search(@AuthenticationPrincipal MemberPrincipal principal,
                                       @RequestBody SearchRequest body,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                       HttpServletRequest request, HttpServletResponse response) {
        return Map.of("recorded", tracking.recordSearch(viewer(principal, request, response),
                body == null ? null : body.keyword(), userAgent));
    }

    @GetMapping("/api/members/me/personalization")
    public Map<String, Object> personalization(@AuthenticationPrincipal MemberPrincipal principal) {
        return Map.of("enabled", personalizationAllowed(principal.getMemberId()),
                "description", "조회·검색·장바구니·찜·구매 이력을 회원님께만 맞춤 추천에 사용합니다. "
                        + "끄면 이력을 추천에 사용하지 않고 인기·신상품만 보여 드립니다.");
    }

    /** 끄면 저장된 취향 프로필과 본인 조회·검색 이력도 함께 삭제합니다. */
    @PutMapping("/api/members/me/personalization")
    public ResponseEntity<Map<String, Object>> updatePersonalization(@AuthenticationPrincipal MemberPrincipal principal,
                                                                     @RequestBody PersonalizationRequest body) {
        Long memberId = principal.getMemberId();
        jdbc.update("update member set personalization_enabled = ? where member_id = ?", body.enabled(), memberId);
        if (!body.enabled()) {
            jdbc.update("delete from customer_product_preference where member_id = ?", memberId);
            jdbc.update("delete from product_view_history where member_id = ?", memberId);
            jdbc.update("delete from customer_search_history where member_id = ?", memberId);
        }
        recommendations.clearCache();
        return ResponseEntity.ok(Map.of("enabled", body.enabled()));
    }

    private Viewer viewer(MemberPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        String session = sessionKey(request, response);
        if (principal == null) {
            return new Viewer(null, session, true);
        }
        return new Viewer(principal.getMemberId(), session, personalizationAllowed(principal.getMemberId()));
    }

    private boolean personalizationAllowed(Long memberId) {
        List<Boolean> rows = jdbc.queryForList(
                "select personalization_enabled from member where member_id = ?", Boolean.class, memberId);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    private String sessionKey(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (SESSION_COOKIE.equals(cookie.getName()) && cookie.getValue() != null
                        && SESSION_PATTERN.matcher(cookie.getValue()).matches()) {
                    return cookie.getValue();
                }
            }
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String value = HexFormat.of().formatHex(bytes);
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, value)
                .httpOnly(true).sameSite("Lax").path("/").maxAge(Duration.ofDays(30))
                .secure(request.isSecure()).build();
        response.addHeader("Set-Cookie", cookie.toString());
        return value;
    }
}
