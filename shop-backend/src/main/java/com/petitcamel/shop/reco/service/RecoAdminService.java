package com.petitcamel.shop.reco.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.ai.domain.PurchaseRecommendation;
import com.petitcamel.shop.ai.repository.PurchaseRecommendationRepository;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.inventory.service.InventoryAnalyticsService;
import com.petitcamel.shop.pricing.domain.PricingPolicy;
import com.petitcamel.shop.pricing.service.PriceCalculator;
import com.petitcamel.shop.pricing.service.PricingPolicyService;
import com.petitcamel.shop.reco.support.ProductProfile;
import com.petitcamel.shop.reco.support.ProductTraits;
import com.petitcamel.shop.reco.support.RecoScoring;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 추천 기능: 미리보기(점수 근거 포함), 매입 추천, 재고 소진 추천, 결정 기록.
 * 모든 계산은 Java/SQL 규칙이며 AI 를 호출하지 않습니다. 발주·가격 변경은 하지 않고 관리자 결정만 기록합니다.
 */
@Service
public class RecoAdminService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;
    private final RecommendationService recommendations;
    private final PopularityService popularity;
    private final CatalogSnapshotService catalog;
    private final RecoSettingsService settingsService;
    private final InventoryAnalyticsService inventoryAnalytics;
    private final PricingPolicyService policyService;
    private final PurchaseRecommendationRepository historyRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RecoAdminService(JdbcTemplate jdbc, RecommendationService recommendations, PopularityService popularity,
                            CatalogSnapshotService catalog, RecoSettingsService settingsService,
                            InventoryAnalyticsService inventoryAnalytics, PricingPolicyService policyService,
                            PurchaseRecommendationRepository historyRepository, AuditLogService auditLogService,
                            ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.recommendations = recommendations;
        this.popularity = popularity;
        this.catalog = catalog;
        this.settingsService = settingsService;
        this.inventoryAnalytics = inventoryAnalytics;
        this.policyService = policyService;
        this.historyRepository = historyRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- previews

    public record PreviewItem(int rank, Long productId, String productName, String group, double price,
                              int stock, double score, Map<String, Double> breakdown, String reason) {
    }

    public record Preview(String type, String algorithm, String basis, List<PreviewItem> items) {
    }

    public Preview previewPersonal(Long memberId, int limit) {
        Boolean allowed = jdbc.query("select personalization_enabled from member where member_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null, memberId);
        if (allowed == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        Viewer viewer = new Viewer(memberId, null, allowed);
        RecommendationService.Computed c = recommendations.computePersonalized(viewer, clampLimit(limit));
        return toPreview("PERSONAL", c, allowed ? "회원 #" + memberId + " 의 조회·찜·장바구니·구매 이력"
                : "회원이 개인화 추천을 끔 → 인기·신상품만 사용");
    }

    public Preview previewSimilar(Long productId, int limit) {
        requireProduct(productId);
        return toPreview("SIMILAR", recommendations.computeSimilar(productId, clampLimit(limit)),
                "카테고리 .30 + 상품명 .15 + 색상 .15 + 가격 .15 + 스타일 .10 + 소재 .05 + 키워드 .10");
    }

    public Preview previewCoordination(Long productId, int limit) {
        requireProduct(productId);
        return toPreview("COORDINATION", recommendations.computeCoordination(List.of(productId), Set.of(productId),
                clampLimit(limit)), "코디 규칙 .35 + 색상 조화 .20 + 스타일 .15 + 계절 .10 + 가격대 .10 + 재고 .10");
    }

    public Preview previewPopular(String listType, int period, int limit) {
        String type = PopularityService.LIST_TYPES.contains(listType) ? listType : "TOP10";
        int days = PopularityService.PERIODS.contains(period) ? period : 7;
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        List<PreviewItem> items = new ArrayList<>();
        for (PopularityService.RankedItem r : popularity.list(type, days)) {
            if (items.size() >= clampLimit(limit)) {
                break;
            }
            ProductProfile p = snapshot.get(r.productId());
            if (p == null) {
                continue;
            }
            items.add(new PreviewItem(r.rank(), p.productId(), p.productName(), p.group(), p.price(),
                    p.availableStock(), r.score(), Map.of(), r.breakdown()));
        }
        return new Preview("POPULAR", type + "_" + days + "D",
                "판매 .40 + 장바구니 .20 + 찜 .15 + 조회 .15 + 최근 증가율 .10 (봇·중복 조회 제외)", items);
    }

    private Preview toPreview(String type, RecommendationService.Computed c, String basis) {
        CatalogSnapshotService.Snapshot snapshot = catalog.snapshot();
        List<PreviewItem> items = new ArrayList<>();
        int rank = 1;
        for (RecoScoring.Scored s : c.items()) {
            ProductProfile p = snapshot.get(s.productId());
            if (p == null) {
                continue;
            }
            items.add(new PreviewItem(rank++, p.productId(), p.productName(), p.group(), p.price(),
                    p.availableStock(), s.score(), s.breakdown(), s.reason()));
        }
        return new Preview(type, c.algorithm(), basis, items);
    }

    // ---------------------------------------------------------------- purchase

    public record PurchaseRow(Long productId, String productName, Long skuId, String skuCode, String optionName,
                              String category, int stock, int available, int pendingInbound, int sales7,
                              int sales30, BigDecimal dailySales, String trend, BigDecimal trendRate,
                              BigDecimal turnover, BigDecimal coverDays, int leadDays, int safetyDays,
                              int targetDays, BigDecimal unitCost, BigDecimal price, BigDecimal marginRate,
                              int suggestedQuantity, int priority, String decision, String formula,
                              List<String> reasons) {
    }

    public record PurchaseReport(LocalDate asOf, List<String> criteria, List<PurchaseRow> rows) {
    }

    @Transactional(readOnly = true)
    public PurchaseReport purchase(String filter) {
        PricingPolicy policy = policyService.current();
        PriceCalculator.Basis basis = PricingPolicyService.basisOf(policy);
        int safetyDays = positive(policy.getSafetyStockDays(), 7);
        int coverTarget = positive(policy.getLowStockCoverDays(), 14);
        int defaultLead = positive(policy.getDefaultLeadDays(), 3);
        BigDecimal standardMargin = policy.getStandardMarginRate();
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        Map<Long, int[]> sales = skuSales(today);
        Map<Long, Integer> inbound = inventoryAnalytics.pendingInbound();
        Map<Long, Integer> leads = supplierLeads();

        List<PurchaseRow> rows = new ArrayList<>();
        for (Map<String, Object> sku : skus("p.status in ('ON_SALE', 'SOLD_OUT')")) {
            long skuId = ((Number) sku.get("sku_id")).longValue();
            int stock = ((Number) sku.get("stock")).intValue();
            int available = stock - ((Number) sku.get("reserved")).intValue();
            int pending = inbound.getOrDefault(skuId, 0);
            int[] s = sales.getOrDefault(skuId, new int[]{0, 0});
            BigDecimal d7 = BigDecimal.valueOf(s[0]).divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP);
            BigDecimal d30 = BigDecimal.valueOf(s[1]).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
            BigDecimal daily = d7.multiply(new BigDecimal("0.6")).add(d30.multiply(new BigDecimal("0.4")))
                    .setScale(3, RoundingMode.HALF_UP);
            BigDecimal trendRate = d30.signum() == 0 ? null
                    : d7.subtract(d30).divide(d30, 3, RoundingMode.HALF_UP);
            String trend = trendRate == null ? (s[0] > 0 ? "NEW_DEMAND" : "NONE")
                    : trendRate.compareTo(new BigDecimal("0.2")) >= 0 ? "UP"
                    : trendRate.compareTo(new BigDecimal("-0.2")) <= 0 ? "DOWN" : "FLAT";
            BigDecimal turnover = stock <= 0 ? null
                    : BigDecimal.valueOf(s[1]).divide(BigDecimal.valueOf(stock), 2, RoundingMode.HALF_UP);
            BigDecimal cover = daily.signum() == 0 ? null
                    : BigDecimal.valueOf(Math.max(available, 0)).divide(daily, 1, RoundingMode.HALF_UP);
            int lead = leads.getOrDefault(skuId, defaultLead);
            int targetDays = lead + safetyDays + coverTarget;
            int need = daily.multiply(BigDecimal.valueOf(targetDays)).setScale(0, RoundingMode.CEILING).intValue();
            int suggested = Math.max(need - Math.max(available, 0) - pending, 0);
            BigDecimal cost = (BigDecimal) sku.get("unit_cost");
            BigDecimal price = (BigDecimal) sku.get("sale_price");
            BigDecimal margin = cost == null || price == null || price.signum() <= 0 ? null
                    : PriceCalculator.marginRate(price, cost, basis);

            List<String> reasons = new ArrayList<>();
            if ("UP".equals(trend)) {
                reasons.add("최근 7일 일판매 " + plain(d7) + "개가 30일 평균 " + plain(d30) + "개보다 "
                        + pct(trendRate) + " 증가");
            } else if ("NEW_DEMAND".equals(trend)) {
                reasons.add("최근 7일 신규 판매 " + s[0] + "개 발생");
            } else if ("DOWN".equals(trend)) {
                reasons.add("최근 7일 판매가 30일 평균보다 " + pct(trendRate.negate()) + " 감소 → 소량 발주 권장");
            }
            if (available <= 0) {
                reasons.add("가용재고 없음(품절)");
            } else if (cover != null && cover.compareTo(BigDecimal.valueOf(lead + safetyDays)) < 0) {
                reasons.add("재고 소진 예상 " + plain(cover) + "일 < 리드타임 " + lead + "일 + 안전재고 " + safetyDays + "일");
            }
            if (pending > 0) {
                reasons.add("입고 예정 " + pending + "개 반영");
            }
            if (margin != null && standardMargin != null && margin.compareTo(standardMargin) >= 0) {
                reasons.add("마진율 " + pct(margin) + " (표준 " + pct(standardMargin) + " 이상)");
            } else if (margin != null && standardMargin != null) {
                reasons.add("마진율 " + pct(margin) + " 로 표준 " + pct(standardMargin) + " 미만 → 매입가 협상 검토");
            }
            String decision;
            int priority;
            if (suggested <= 0) {
                decision = daily.signum() == 0 ? "NO_SALES" : "ENOUGH";
                priority = 5;
                reasons.add(daily.signum() == 0 ? "최근 30일 판매 없음 → 발주 불필요"
                        : "필요량 " + need + "개 ≤ 가용 " + Math.max(available, 0) + " + 입고예정 " + pending + "개 → 재고 충분");
            } else {
                decision = "REORDER";
                priority = available <= 0 ? 1
                        : cover != null && cover.compareTo(BigDecimal.valueOf(lead)) <= 0 ? 1
                        : cover != null && cover.compareTo(BigDecimal.valueOf(lead + safetyDays)) <= 0 ? 2
                        : "UP".equals(trend) ? 2 : 3;
                if (margin != null && standardMargin != null && margin.compareTo(standardMargin) < 0) {
                    priority = Math.min(5, priority + 1);
                }
                reasons.add("권장 발주 " + suggested + "개");
            }
            if (!"ALL".equalsIgnoreCase(filter) && !"REORDER".equals(decision)) {
                continue;
            }
            String formula = "일평균 = 0.6×(" + s[0] + "÷7) + 0.4×(" + s[1] + "÷30) = " + plain(daily)
                    + " / 필요량 = 일평균 × (리드 " + lead + " + 안전 " + safetyDays + " + 판매커버 " + coverTarget
                    + ")일 = " + need + " / 권장 = 필요량 − 가용 " + Math.max(available, 0) + " − 입고예정 " + pending
                    + " = " + suggested;
            rows.add(new PurchaseRow(((Number) sku.get("product_id")).longValue(), (String) sku.get("product_name"),
                    skuId, (String) sku.get("sku_code"), sku.get("color") + "/" + sku.get("size"),
                    (String) sku.get("category_name"), stock, available, pending, s[0], s[1], daily, trend, trendRate,
                    turnover, cover, lead, safetyDays, targetDays, cost, price, margin, suggested, priority, decision,
                    formula, reasons));
        }
        rows.sort(Comparator.comparingInt(PurchaseRow::priority)
                .thenComparing(r -> r.coverDays() == null ? BigDecimal.valueOf(Long.MAX_VALUE) : r.coverDays())
                .thenComparing(PurchaseRow::sales7, Comparator.reverseOrder()));
        return new PurchaseReport(today, List.of(
                "일평균 판매 = 0.6 × 최근7일/7 + 0.4 × 최근30일/30 (결제 완료, 취소·반품 제외)",
                "추세: 7일 일평균이 30일 일평균 대비 +20% 이상 증가 UP, −20% 이하 DOWN",
                "필요량 = 일평균 × (리드타임 + 안전재고 " + safetyDays + "일 + 판매커버 " + coverTarget + "일)",
                "권장 발주량 = 필요량 − 가용재고 − 입고예정 (0 미만이면 0)",
                "리드타임: 최근 매입 거래처 설정값 → 기본 " + defaultLead + "일",
                "추천은 참고용입니다. 자동 발주하지 않으며 실제 발주는 관리자가 진행합니다."),
                rows.size() > 500 ? rows.subList(0, 500) : rows);
    }

    // ---------------------------------------------------------------- slow moving

    public record SlowRow(Long productId, String productName, String category, String status, int stock,
                          int sales30, int sales90, LocalDate lastSoldDate, Integer daysSinceLastSale,
                          int daysOnHand, BigDecimal coverDays, String season, BigDecimal unitCost, BigDecimal price,
                          BigDecimal marginRate, BigDecimal stockValue, List<String> classes,
                          List<String> strategies, BigDecimal suggestedDiscountRate, BigDecimal maxDiscountRate,
                          BigDecimal suggestedPrice, BigDecimal floorPrice, BigDecimal marginAfterDiscount,
                          List<String> reasons) {
    }

    public record SlowReport(LocalDate asOf, BigDecimal minimumMarginRate, BigDecimal maxDiscountRate,
                             List<String> criteria, List<SlowRow> rows) {
    }

    @Transactional(readOnly = true)
    public SlowReport slowMoving(String classFilter) {
        PricingPolicy policy = policyService.current();
        PriceCalculator.Basis basis = PricingPolicyService.basisOf(policy);
        BigDecimal minMargin = policy.getMinimumMarginRate() == null ? BigDecimal.ZERO : policy.getMinimumMarginRate();
        BigDecimal standardMargin = policy.getStandardMarginRate();
        int maxSales = policy.getSlowMovingMaxSales() == null ? 2 : policy.getSlowMovingMaxSales();
        BigDecimal maxDiscount = settingsService.settings().maxDiscountRate();
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        Set<String> nowSeasons = Set.of(ProductTraits.seasonOf(today.getMonth()),
                ProductTraits.seasonOf(today.plusMonths(1).getMonth()));

        List<SlowRow> rows = new ArrayList<>();
        jdbc.query("""
                select p.product_id, p.product_name, p.status, p.sale_price, p.purchase_price, p.season, p.created_at,
                       c.name as category_name,
                       coalesce(sum(i.stock_quantity), 0) as stock,
                       sum(coalesce(sc.avg_unit_cost, p.purchase_price) * coalesce(i.stock_quantity, 0))
                           / nullif(sum(case when coalesce(sc.avg_unit_cost, p.purchase_price) is null then 0
                                             else coalesce(i.stock_quantity, 0) end), 0) as unit_cost,
                       (select max(m.created_at) from inventory_movement m join product_sku s2 on s2.sku_id = m.sku_id
                         where s2.product_id = p.product_id and m.movement_type = 'RECEIPT') as last_received,
                       sales.s30, sales.s90, sales.last_sold
                from product p
                left join category c on c.category_id = p.category_id
                join product_sku s on s.product_id = p.product_id and s.status <> 'DISCONTINUED'
                left join inventory i on i.sku_id = s.sku_id
                left join sku_cost sc on sc.sku_id = s.sku_id
                left join lateral (
                    select coalesce(sum(case when pay.approved_at >= ? then oi.quantity else 0 end), 0) as s30,
                           coalesce(sum(oi.quantity), 0) as s90, max(pay.approved_at) as last_sold
                    from order_item oi
                    join (select order_id, min(approved_at) as approved_at from payment
                          where approved_at is not null group by order_id) pay on pay.order_id = oi.order_id
                    where oi.product_id = p.product_id and oi.status = 'ORDERED' and pay.approved_at >= ?) sales on true
                where p.status in ('ON_SALE', 'SOLD_OUT', 'HIDDEN')
                group by p.product_id, c.name, sales.s30, sales.s90, sales.last_sold
                having coalesce(sum(i.stock_quantity), 0) > 0""", rs -> {
            long productId = rs.getLong("product_id");
            int stock = rs.getInt("stock");
            int s30 = rs.getInt("s30");
            int s90 = rs.getInt("s90");
            Timestamp lastSoldTs = rs.getTimestamp("last_sold");
            LocalDate lastSold = lastSoldTs == null ? null : lastSoldTs.toInstant().atZone(ZONE).toLocalDate();
            Timestamp receivedTs = rs.getTimestamp("last_received");
            Instant since = receivedTs != null ? receivedTs.toInstant() : rs.getTimestamp("created_at").toInstant();
            int daysOnHand = (int) ChronoUnit.DAYS.between(since.atZone(ZONE).toLocalDate(), today);
            Integer sinceSale = lastSold == null ? null : (int) ChronoUnit.DAYS.between(lastSold, today);
            BigDecimal daily = BigDecimal.valueOf(s30).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
            BigDecimal cover = daily.signum() == 0 ? null
                    : BigDecimal.valueOf(stock).divide(daily, 0, RoundingMode.HALF_UP);
            BigDecimal price = rs.getBigDecimal("sale_price");
            BigDecimal cost = rs.getBigDecimal("unit_cost");
            if (cost != null) {
                cost = cost.setScale(0, RoundingMode.HALF_UP);
            }
            BigDecimal margin = cost == null || price == null || price.signum() <= 0 ? null
                    : PriceCalculator.marginRate(price, cost, basis);
            String seasonText = rs.getString("season");
            Set<String> seasons = ProductTraits.seasons((seasonText == null ? "" : seasonText) + " "
                    + rs.getString("product_name"));

            List<String> classes = new ArrayList<>();
            List<String> reasons = new ArrayList<>();
            if ((sinceSale == null || sinceSale >= 60) && daysOnHand >= 60) {
                classes.add("LONG_UNSOLD");
                reasons.add(sinceSale == null ? "입고 후 " + daysOnHand + "일 동안 판매 없음"
                        : "마지막 판매 " + sinceSale + "일 전 (60일 이상 미판매)");
            }
            if ((cover != null && cover.intValue() > 90) || (s30 == 0 && stock >= 10)) {
                classes.add("OVERSTOCK");
                reasons.add(cover == null ? "재고 " + stock + "개, 최근 30일 판매 없음"
                        : "재고 " + stock + "개 = 현재 판매속도로 " + cover + "일분 (90일 초과)");
            }
            if (!seasons.isEmpty() && !seasons.contains("사계절")
                    && seasons.stream().noneMatch(nowSeasons::contains)) {
                classes.add("SEASON_ENDING");
                reasons.add("상품 시즌(" + String.join(",", seasons) + ")이 현재·다음 달 시즌("
                        + String.join(",", nowSeasons) + ")과 다름");
            }
            if (margin != null && standardMargin != null && margin.compareTo(standardMargin) >= 0
                    && s30 <= maxSales && daysOnHand >= 30) {
                classes.add("HIGH_MARGIN_LOW_SALES");
                reasons.add("마진율 " + pct(margin) + " (표준 " + pct(standardMargin) + " 이상)인데 30일 판매 "
                        + s30 + "개");
            }
            if (classes.isEmpty()) {
                return;
            }

            BigDecimal floor = null;
            BigDecimal maxRate = BigDecimal.ZERO;
            BigDecimal suggestedRate = BigDecimal.ZERO;
            BigDecimal suggestedPrice = null;
            BigDecimal marginAfter = null;
            if (price != null && price.signum() > 0 && cost != null) {
                BigDecimal marginFloor = PriceCalculator.targetPrice(cost, minMargin, basis);
                BigDecimal discountFloor = price.multiply(BigDecimal.ONE.subtract(maxDiscount));
                floor = marginFloor.max(discountFloor).setScale(0, RoundingMode.CEILING);
                maxRate = floor.compareTo(price) >= 0 ? BigDecimal.ZERO
                        : BigDecimal.ONE.subtract(floor.divide(price, 4, RoundingMode.CEILING)).max(BigDecimal.ZERO);
                BigDecimal wanted = classes.contains("SEASON_ENDING") ? new BigDecimal("0.25")
                        : classes.contains("LONG_UNSOLD") ? new BigDecimal("0.20")
                        : classes.contains("OVERSTOCK") ? new BigDecimal("0.15") : new BigDecimal("0.10");
                suggestedRate = wanted.min(maxRate);
                if (suggestedRate.compareTo(new BigDecimal("0.03")) >= 0) {
                    long rounded = PriceCalculator.round(price.multiply(BigDecimal.ONE.subtract(suggestedRate)),
                            policy.getRoundingMode());
                    BigDecimal candidate = BigDecimal.valueOf(rounded);
                    if (candidate.compareTo(floor) < 0) {
                        candidate = floor;
                    }
                    if (candidate.compareTo(price) < 0) {
                        suggestedPrice = candidate;
                        suggestedRate = BigDecimal.ONE.subtract(candidate.divide(price, 4, RoundingMode.HALF_UP));
                        marginAfter = PriceCalculator.marginRate(candidate, cost, basis);
                        classes.add("DISCOUNT_REVIEW");
                    } else {
                        suggestedRate = BigDecimal.ZERO;
                    }
                } else {
                    suggestedRate = BigDecimal.ZERO;
                }
                if (suggestedPrice == null) {
                    reasons.add("최소 마진율 " + pct(minMargin) + " 을 지키면 할인 여력 없음 → 노출·코디 연결 위주");
                }
            } else {
                reasons.add("원가 미확인 → 할인율을 계산하지 않음 (매입 원가 등록 필요)");
            }
            List<String> strategies = strategies(classes, suggestedPrice != null);
            BigDecimal value = cost == null ? null : cost.multiply(BigDecimal.valueOf(stock));
            rows.add(new SlowRow(productId, rs.getString("product_name"), rs.getString("category_name"),
                    rs.getString("status"), stock, s30, s90, lastSold, sinceSale, daysOnHand, cover,
                    seasons.isEmpty() ? null : String.join(",", seasons), cost, price, margin, value,
                    List.copyOf(classes), strategies, suggestedRate, maxRate, suggestedPrice, floor, marginAfter,
                    reasons));
        }, start(today.minusDays(29)), start(today.minusDays(89)));
        List<SlowRow> filtered = classFilter == null || classFilter.isBlank() ? rows
                : rows.stream().filter(r -> r.classes().contains(classFilter)).toList();
        List<SlowRow> sorted = new ArrayList<>(filtered);
        sorted.sort(Comparator.comparing((SlowRow r) -> r.stockValue() == null ? BigDecimal.ZERO : r.stockValue())
                .reversed());
        return new SlowReport(today, minMargin, maxDiscount, List.of(
                "LONG_UNSOLD: 60일 이상 판매 없음 + 입고(또는 등록) 후 60일 경과",
                "OVERSTOCK: 현재 판매속도(30일 기준)로 90일 넘게 팔아야 하는 재고, 또는 30일 무판매 재고 10개 이상",
                "SEASON_ENDING: 상품 시즌이 이번 달·다음 달 시즌과 다름",
                "HIGH_MARGIN_LOW_SALES: 표준 마진율 이상인데 30일 판매 " + maxSales + "개 이하",
                "DISCOUNT_REVIEW: 최소 마진율 " + pct(minMargin) + "과 최대 할인율 " + pct(maxDiscount)
                        + "을 모두 지키면서 3% 이상 할인 가능한 상품",
                "할인 하한가 = max(원가 기준 최소 마진 가격, 판매가 × (1 − 최대 할인율)), 가격 반올림 규칙 적용",
                "가격은 자동으로 바꾸지 않습니다. 관리자가 승인한 뒤 상품 관리에서 직접 변경하세요."),
                sorted.size() > 500 ? sorted.subList(0, 500) : sorted);
    }

    private static List<String> strategies(List<String> classes, boolean discountable) {
        List<String> s = new ArrayList<>();
        if (classes.contains("HIGH_MARGIN_LOW_SALES") || classes.contains("LONG_UNSOLD")) {
            s.add("메인·추천 영역 노출 강화(추천 관리 > 노출 부스트 규칙)");
        }
        s.add("인기 상품의 코디 추천에 연결");
        if (classes.contains("OVERSTOCK")) {
            s.add("묶음 구매·세트 구성 검토");
        }
        if (discountable) {
            s.add(classes.contains("SEASON_ENDING") ? "시즌 오프 할인 검토" : "기간 한정 쿠폰·할인 검토");
        }
        if (classes.contains("SEASON_ENDING") || classes.contains("LONG_UNSOLD")) {
            s.add("추가 매입 중단");
        }
        return s;
    }

    // ---------------------------------------------------------------- decisions

    public record DecisionRequest(String type, Long productId, Long skuId, String decision, Integer quantity,
                                  String note, Map<String, Object> metrics) {
    }

    public record DecisionView(Long id, String type, Long productId, Long skuId, String status, Integer quantity,
                               String reason, Instant decidedAt) {
    }

    @Transactional
    public DecisionView decide(DecisionRequest r, Long actorId) {
        if (!"PURCHASE".equals(r.type()) && !"SLOW_MOVING".equals(r.type())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "type 은 PURCHASE 또는 SLOW_MOVING 입니다.");
        }
        if (!"ACCEPTED".equals(r.decision()) && !"DISMISSED".equals(r.decision())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "decision 은 ACCEPTED 또는 DISMISSED 입니다.");
        }
        requireProduct(r.productId());
        if (r.quantity() != null && (r.quantity() < 0 || r.quantity() > 100000)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "수량이 올바르지 않습니다.");
        }
        ObjectNode metrics = r.metrics() == null ? objectMapper.createObjectNode()
                : objectMapper.valueToTree(r.metrics());
        String note = r.note() == null ? "" : r.note().trim();
        String reason = ("PURCHASE".equals(r.type()) ? "[매입 추천 " : "[재고 소진 추천 ")
                + ("ACCEPTED".equals(r.decision()) ? "승인" : "보류") + "] " + note;
        Instant now = clock.instant();
        PurchaseRecommendation e = new PurchaseRecommendation();
        e.setRecommendationType(r.type());
        e.setProductId(r.productId());
        e.setSkuId(r.skuId());
        e.setPriority(3);
        e.setSuggestedQuantity(r.quantity());
        String json = metrics.toString();
        e.setMetricsJson(json.length() > 8000 ? "{}" : json);
        e.setReason(reason.length() > 2000 ? reason.substring(0, 2000) : reason);
        e.setProvider("RULE");
        e.setStatus(r.decision());
        e.setCreatedBy(actorId);
        e.setCreatedAt(now);
        e.setDecidedBy(actorId);
        e.setDecidedAt(now);
        historyRepository.save(e);
        auditLogService.record(actorId, "RECO_" + r.type() + "_" + r.decision(), "AI_RECOMMENDATION",
                String.valueOf(e.getId()), "productId=" + r.productId() + ", skuId=" + r.skuId()
                        + ", qty=" + r.quantity());
        return new DecisionView(e.getId(), r.type(), r.productId(), r.skuId(), r.decision(), r.quantity(),
                e.getReason(), now);
    }

    @Transactional(readOnly = true)
    public List<DecisionView> decisions(String type, int limit) {
        return jdbc.query("""
                select ai_recommendation_history_id, recommendation_type, product_id, sku_id, status,
                       suggested_quantity, reason, decided_at
                from ai_recommendation_history
                where recommendation_type = ? and provider = 'RULE' and decided_at is not null
                order by decided_at desc, ai_recommendation_history_id desc limit ?""",
                (rs, i) -> new DecisionView(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        (Long) rs.getObject(4, Long.class), rs.getString(5),
                        (Integer) rs.getObject(6, Integer.class), rs.getString(7), rs.getTimestamp(8).toInstant()),
                type, Math.max(1, Math.min(limit, 200)));
    }

    // ---------------------------------------------------------------- helpers

    private List<Map<String, Object>> skus(String productCondition) {
        return jdbc.queryForList("""
                select s.sku_id, s.sku_code, s.color, s.size, p.product_id, p.product_name, p.sale_price,
                       c.name as category_name, coalesce(i.stock_quantity, 0) as stock,
                       coalesce(i.reserved_quantity, 0) as reserved,
                       coalesce(sc.avg_unit_cost, p.purchase_price) as unit_cost
                from product_sku s
                join product p on p.product_id = s.product_id
                left join category c on c.category_id = p.category_id
                left join inventory i on i.sku_id = s.sku_id
                left join sku_cost sc on sc.sku_id = s.sku_id
                where s.status <> 'DISCONTINUED'
                """ + " and " + productCondition + " order by p.product_id, s.sku_id");
    }

    /** sku_id → [최근 7일, 최근 30일] 순판매 수량. */
    private Map<Long, int[]> skuSales(LocalDate today) {
        Map<Long, int[]> result = new HashMap<>();
        jdbc.query("""
                select oi.sku_id,
                       coalesce(sum(case when pay.approved_at >= ? then oi.quantity else 0 end), 0),
                       coalesce(sum(oi.quantity), 0)
                from order_item oi
                join (select order_id, min(approved_at) as approved_at from payment
                      where approved_at is not null group by order_id) pay on pay.order_id = oi.order_id
                where oi.status = 'ORDERED' and oi.sku_id is not null and pay.approved_at >= ?
                group by oi.sku_id""", rs -> {
            result.put(rs.getLong(1), new int[]{rs.getInt(2), rs.getInt(3)});
        }, start(today.minusDays(6)), start(today.minusDays(29)));
        return result;
    }

    private Map<Long, Integer> supplierLeads() {
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbc.query("""
                select distinct on (h.sku_id) h.sku_id, sp.lead_days
                from product_cost_history h
                left join supplier sp on sp.supplier_id = h.supplier_id
                where h.sku_id is not null
                order by h.sku_id, h.received_at desc""", rs -> {
            int lead = rs.getInt(2);
            if (!rs.wasNull() && lead > 0) {
                result.put(rs.getLong(1), lead);
            }
        });
        return result;
    }

    private void requireProduct(Long productId) {
        Integer count = productId == null ? Integer.valueOf(0)
                : jdbc.queryForObject("select count(*) from product where product_id = ?", Integer.class, productId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
    }

    private static Timestamp start(LocalDate day) {
        return Timestamp.from(day.atStartOfDay(ZONE).toInstant());
    }

    private static int clampLimit(int limit) {
        return limit <= 0 ? 12 : Math.min(limit, 50);
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String pct(BigDecimal rate) {
        return rate == null ? "-" : rate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }
}
