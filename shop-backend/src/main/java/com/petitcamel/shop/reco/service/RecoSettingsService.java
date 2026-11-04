package com.petitcamel.shop.reco.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.reco.support.RecoScoring;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** {@code recommendation_settings} (singleton) and {@code recommendation_rule}. Cached briefly in memory. */
@Service
public class RecoSettingsService {

    private static final long CACHE_MS = 10_000;
    private static final Set<String> RULE_TYPES = Set.of("COORDINATION", "EXCLUDE_PRODUCT", "CATEGORY_BOOST");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private volatile Settings cached;
    private volatile long cachedAt;
    private volatile List<Rule> cachedRules;
    private volatile long rulesCachedAt;

    public RecoSettingsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Settings(boolean enabled, boolean homeEnabled, boolean detailEnabled, boolean cartEnabled,
                           boolean mypageEnabled, int displayCount, int cacheMinutes, int minStock,
                           int attributionDays, int popularDays, BigDecimal wCategory, BigDecimal wColor,
                           BigDecimal wPrice, BigDecimal wPurchase, BigDecimal wPopularity, BigDecimal wFreshness,
                           BigDecimal maxDiscountRate, Instant updatedAt) {
        public RecoScoring.Weights weights() {
            return new RecoScoring.Weights(wCategory.doubleValue(), wColor.doubleValue(), wPrice.doubleValue(),
                    wPurchase.doubleValue(), wPopularity.doubleValue(), wFreshness.doubleValue());
        }

        public boolean placementEnabled(String placement) {
            if (!enabled) {
                return false;
            }
            if (placement.startsWith("HOME")) {
                return homeEnabled;
            }
            if (placement.startsWith("DETAIL")) {
                return detailEnabled;
            }
            if (placement.startsWith("CART")) {
                return cartEnabled;
            }
            if (placement.startsWith("MYPAGE")) {
                return mypageEnabled;
            }
            return true;
        }
    }

    public record Rule(Long id, String ruleType, String sourceValue, String targetValue, Long productId,
                       BigDecimal weight, String memo, boolean active, Instant updatedAt) {
    }

    public record RuleRequest(String ruleType, String sourceValue, String targetValue, Long productId,
                              BigDecimal weight, String memo, Boolean active) {
    }

    public Settings settings() {
        Settings current = cached;
        if (current != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            return current;
        }
        current = jdbc.queryForObject("""
                select enabled, home_enabled, detail_enabled, cart_enabled, mypage_enabled, display_count,
                       cache_minutes, min_stock, attribution_days, popular_days, w_category, w_color, w_price,
                       w_purchase, w_popularity, w_freshness, max_discount_rate, updated_at
                from recommendation_settings where settings_id = 1""", (rs, i) -> new Settings(
                rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5),
                rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getBigDecimal(11),
                rs.getBigDecimal(12), rs.getBigDecimal(13), rs.getBigDecimal(14), rs.getBigDecimal(15),
                rs.getBigDecimal(16), rs.getBigDecimal(17), rs.getTimestamp(18).toInstant()));
        cached = current;
        cachedAt = System.currentTimeMillis();
        return current;
    }

    @Transactional
    public Settings update(Settings s, Long actorId) {
        if (s.displayCount() < 1 || s.displayCount() > 30) {
            throw invalid("노출 개수는 1~30 사이여야 합니다.");
        }
        if (s.cacheMinutes() < 0 || s.cacheMinutes() > 1440) {
            throw invalid("캐시 시간은 0~1440분 사이여야 합니다.");
        }
        if (s.minStock() < 0 || s.attributionDays() < 1 || s.attributionDays() > 90
                || s.popularDays() < 1 || s.popularDays() > 90) {
            throw invalid("최소 재고·구매 기여 기간·인기 기간 값을 확인해 주세요.");
        }
        for (BigDecimal w : List.of(s.wCategory(), s.wColor(), s.wPrice(), s.wPurchase(), s.wPopularity(),
                s.wFreshness())) {
            if (w == null || w.signum() < 0 || w.compareTo(new BigDecimal("9.999")) > 0) {
                throw invalid("가중치는 0~9.999 사이여야 합니다.");
            }
        }
        if (s.weights().sum() <= 0) {
            throw invalid("가중치 합계가 0보다 커야 합니다.");
        }
        if (s.maxDiscountRate() == null || s.maxDiscountRate().signum() < 0
                || s.maxDiscountRate().compareTo(BigDecimal.ONE) >= 0) {
            throw invalid("최대 할인율은 0 이상 1 미만이어야 합니다.");
        }
        jdbc.update("""
                update recommendation_settings set enabled = ?, home_enabled = ?, detail_enabled = ?,
                       cart_enabled = ?, mypage_enabled = ?, display_count = ?, cache_minutes = ?, min_stock = ?,
                       attribution_days = ?, popular_days = ?, w_category = ?, w_color = ?, w_price = ?,
                       w_purchase = ?, w_popularity = ?, w_freshness = ?, max_discount_rate = ?, updated_by = ?,
                       updated_at = ?, version = version + 1
                where settings_id = 1""",
                s.enabled(), s.homeEnabled(), s.detailEnabled(), s.cartEnabled(), s.mypageEnabled(),
                s.displayCount(), s.cacheMinutes(), s.minStock(), s.attributionDays(), s.popularDays(),
                s.wCategory(), s.wColor(), s.wPrice(), s.wPurchase(), s.wPopularity(), s.wFreshness(),
                s.maxDiscountRate(), actorId, Timestamp.from(clock.instant()));
        invalidate();
        return settings();
    }

    public List<Rule> rules() {
        List<Rule> current = cachedRules;
        if (current != null && System.currentTimeMillis() - rulesCachedAt < CACHE_MS) {
            return current;
        }
        current = jdbc.query("""
                select rule_id, rule_type, source_value, target_value, product_id, weight, memo, active, updated_at
                from recommendation_rule order by rule_type, rule_id""", (rs, i) -> new Rule(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                (Long) rs.getObject(5, Long.class), rs.getBigDecimal(6), rs.getString(7), rs.getBoolean(8),
                rs.getTimestamp(9).toInstant()));
        cachedRules = List.copyOf(current);
        rulesCachedAt = System.currentTimeMillis();
        return cachedRules;
    }

    public List<Rule> activeRules(String type) {
        return rules().stream().filter(r -> r.active() && type.equals(r.ruleType())).toList();
    }

    public Set<Long> excludedProducts() {
        return Set.copyOf(activeRules("EXCLUDE_PRODUCT").stream().map(Rule::productId).toList());
    }

    @Transactional
    public Rule createRule(RuleRequest request, Long actorId) {
        validate(request);
        Instant now = clock.instant();
        Long id = jdbc.queryForObject("""
                insert into recommendation_rule (rule_type, source_value, target_value, product_id, weight, memo,
                                                 active, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning rule_id""", Long.class,
                request.ruleType(), trim(request.sourceValue()), trim(request.targetValue()), request.productId(),
                request.weight() == null ? BigDecimal.ONE : request.weight(), trim(request.memo()),
                request.active() == null || request.active(), actorId, Timestamp.from(now), Timestamp.from(now));
        invalidate();
        return rules().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public Rule updateRule(Long id, RuleRequest request) {
        validate(request);
        int updated = jdbc.update("""
                update recommendation_rule set rule_type = ?, source_value = ?, target_value = ?, product_id = ?,
                       weight = ?, memo = ?, active = ?, updated_at = ? where rule_id = ?""",
                request.ruleType(), trim(request.sourceValue()), trim(request.targetValue()), request.productId(),
                request.weight() == null ? BigDecimal.ONE : request.weight(), trim(request.memo()),
                request.active() == null || request.active(), Timestamp.from(clock.instant()), id);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "추천 규칙을 찾을 수 없습니다.");
        }
        invalidate();
        return rules().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    /** Rules are switched off rather than deleted so past recommendations stay explainable. */
    @Transactional
    public void deactivateRule(Long id) {
        if (jdbc.update("update recommendation_rule set active = false, updated_at = ? where rule_id = ?",
                Timestamp.from(clock.instant()), id) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "추천 규칙을 찾을 수 없습니다.");
        }
        invalidate();
    }

    public void invalidate() {
        cached = null;
        cachedRules = null;
    }

    private void validate(RuleRequest r) {
        if (r == null || r.ruleType() == null || !RULE_TYPES.contains(r.ruleType())) {
            throw invalid("규칙 종류를 선택해 주세요.");
        }
        if (r.weight() != null && (r.weight().signum() < 0 || r.weight().compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw invalid("가중치는 0~5 사이여야 합니다.");
        }
        switch (r.ruleType()) {
            case "COORDINATION" -> {
                if (isBlank(r.sourceValue()) || isBlank(r.targetValue())) {
                    throw invalid("코디 규칙은 기준 품목과 추천 품목을 모두 입력해야 합니다.");
                }
            }
            case "EXCLUDE_PRODUCT" -> {
                if (r.productId() == null) {
                    throw invalid("제외할 상품 ID를 입력해 주세요.");
                }
            }
            default -> {
                if (isBlank(r.sourceValue())) {
                    throw invalid("가중치를 적용할 카테고리·품목을 입력해 주세요.");
                }
            }
        }
        for (String value : new String[]{r.sourceValue(), r.targetValue()}) {
            if (value != null && value.trim().length() > 50) {
                throw invalid("품목 이름은 50자 이내로 입력해 주세요.");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
