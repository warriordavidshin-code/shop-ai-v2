package com.petitcamel.shop.inventory.service;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.inventory.dto.InventoryAnalyticsDtos;
import com.petitcamel.shop.pricing.domain.PricingPolicy;
import com.petitcamel.shop.pricing.service.PricingPolicyService;
import com.petitcamel.shop.purchase.repository.PurchaseReceiptItemRepository;
import com.petitcamel.shop.purchase.service.SupplierService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
import java.util.Locale;
import java.util.Map;

/**
 * Read-only stock analysis: 재고 현황, 입출고 이력, 재고 부족(고정 기준 + 판매속도 기준), 장기 미판매.
 * Every result carries the period and the figures it was derived from so the admin can check the reasoning.
 */
@Service
public class InventoryAnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public static final Map<String, String> MOVEMENT_LABELS = Map.of(
            "RECEIPT", "매입입고",
            "SALE", "주문출고",
            "CANCEL", "주문취소 복구",
            "RETURN", "반품입고",
            "ADJUSTMENT", "재고조정",
            "DAMAGE", "파손 및 폐기",
            "RESERVATION", "주문 예약",
            "RELEASE", "예약 해제");

    private final EntityManager entityManager;
    private final PricingPolicyService policyService;
    private final PurchaseReceiptItemRepository receiptItemRepository;
    private final SupplierService supplierService;
    private final Clock clock;

    public InventoryAnalyticsService(EntityManager entityManager, PricingPolicyService policyService,
                                     PurchaseReceiptItemRepository receiptItemRepository,
                                     SupplierService supplierService, Clock clock) {
        this.entityManager = entityManager;
        this.policyService = policyService;
        this.receiptItemRepository = receiptItemRepository;
        this.supplierService = supplierService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryAnalyticsDtos.TransactionRow> transactions(
            Long skuId, Long productId, String type, LocalDate from, LocalDate to, String keyword, int page, int size) {
        int safeSize = size <= 0 ? 30 : Math.min(size, 200);
        int safePage = Math.max(page, 0);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        if (skuId != null) {
            where.append(" AND m.sku_id = :skuId");
            params.put("skuId", skuId);
        }
        if (productId != null) {
            where.append(" AND s.product_id = :productId");
            params.put("productId", productId);
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND m.movement_type = :type");
            params.put("type", type.trim().toUpperCase(Locale.ROOT));
        }
        if (from != null) {
            where.append(" AND m.created_at >= :from");
            params.put("from", Timestamp.from(from.atStartOfDay(ZONE).toInstant()));
        }
        if (to != null) {
            where.append(" AND m.created_at < :to");
            params.put("to", Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant()));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (LOWER(p.product_name) LIKE :kw OR LOWER(s.sku_code) LIKE :kw OR m.reference_id LIKE :kw)");
            params.put("kw", "%" + keyword.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%");
        }
        String from_ = """
                 FROM inventory_movement m
                 JOIN product_sku s ON s.sku_id = m.sku_id
                 JOIN product p ON p.product_id = s.product_id
                 LEFT JOIN purchase_receipt r ON r.purchase_receipt_id = m.purchase_receipt_id
                 LEFT JOIN purchase_ledger l ON l.purchase_ledger_id = r.purchase_ledger_id
                 LEFT JOIN member mb ON mb.member_id = m.actor_member_id
                """;
        Query count = entityManager.createNativeQuery("SELECT COUNT(*)" + from_ + where);
        Query select = entityManager.createNativeQuery("""
                SELECT m.movement_id, m.created_at, m.movement_type, p.product_id, p.product_name, s.sku_id,
                       s.sku_code, s.color, s.size, m.quantity, m.stock_before, m.stock_after, m.reference_type,
                       m.reference_id, m.purchase_receipt_id, r.receipt_no, l.management_no, m.unit_cost, m.reason,
                       m.actor_member_id, mb.name
                """ + from_ + where + " ORDER BY m.created_at DESC, m.movement_id DESC");
        params.forEach((k, v) -> {
            count.setParameter(k, v);
            select.setParameter(k, v);
        });
        long total = ((Number) count.getSingleResult()).longValue();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = select.setFirstResult(safePage * safeSize).setMaxResults(safeSize).getResultList();
        List<InventoryAnalyticsDtos.TransactionRow> content = rows.stream().map(r -> {
            String movementType = (String) r[2];
            return new InventoryAnalyticsDtos.TransactionRow(lng(r[0]), instant(r[1]), movementType,
                    MOVEMENT_LABELS.getOrDefault(movementType, movementType), lng(r[3]), (String) r[4], lng(r[5]),
                    (String) r[6], r[7] + "/" + r[8], ((Number) r[9]).intValue(), integer(r[10]), integer(r[11]),
                    (String) r[12], (String) r[13], lng(r[14]), (String) r[15], (String) r[16], dec(r[17]),
                    (String) r[18], lng(r[19]), (String) r[20]);
        }).toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional(readOnly = true)
    public InventoryAnalyticsDtos.StockStatusResponse status(String keyword, String filter) {
        List<SkuBase> skus = loadSkus();
        Map<Long, Integer> pending = pendingInbound();
        List<InventoryAnalyticsDtos.StockRow> rows = new ArrayList<>();
        long totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        int soldOut = 0;
        int low = 0;
        int unknownCost = 0;
        String kw = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        for (SkuBase sku : skus) {
            int available = sku.stock - sku.reserved;
            String status = available <= 0 ? "SOLD_OUT" : available <= sku.reorderPoint ? "LOW" : "NORMAL";
            BigDecimal value = sku.unitCost == null ? null
                    : sku.unitCost.multiply(BigDecimal.valueOf(sku.stock)).setScale(0, RoundingMode.HALF_UP);
            totalQty += sku.stock;
            if (value != null) {
                totalValue = totalValue.add(value);
            } else if (sku.stock > 0) {
                unknownCost++;
            }
            if ("SOLD_OUT".equals(status)) {
                soldOut++;
            } else if ("LOW".equals(status)) {
                low++;
            }
            if (kw != null && !kw.isEmpty() && !sku.productName.toLowerCase(Locale.ROOT).contains(kw)
                    && !sku.skuCode.toLowerCase(Locale.ROOT).contains(kw)) {
                continue;
            }
            if (filter != null && !filter.isBlank() && !filter.equalsIgnoreCase(status)) {
                continue;
            }
            rows.add(new InventoryAnalyticsDtos.StockRow(sku.productId, sku.productName, sku.productStatus, sku.skuId,
                    sku.skuCode, sku.option(), sku.stock, sku.reserved, available, sku.reorderPoint,
                    pending.getOrDefault(sku.skuId, 0), sku.unitCost, sku.costSource, value, status));
        }
        return new InventoryAnalyticsDtos.StockStatusResponse(skus.size(), totalQty, totalValue, soldOut, low,
                unknownCost, rows.size() > 1000 ? rows.subList(0, 1000) : rows);
    }

    @Transactional(readOnly = true)
    public InventoryAnalyticsDtos.AnalysisResponse<InventoryAnalyticsDtos.LowStockRow> lowStock() {
        PricingPolicy policy = policyService.current();
        int window = positive(policy.getSalesWindowDays(), 30);
        int safetyDays = positive(policy.getSafetyStockDays(), 7);
        int coverTarget = positive(policy.getLowStockCoverDays(), 14);
        int defaultLead = positive(policy.getDefaultLeadDays(), 3);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        Map<Long, Sales> sales = sales(today.minusDays(window - 1L));
        Map<Long, Integer> pending = pendingInbound();
        Map<Long, Object[]> suppliers = latestSupplierBySku();
        Map<Long, BigDecimal> averageLead = supplierService.averageLeadDays();

        List<InventoryAnalyticsDtos.LowStockRow> rows = new ArrayList<>();
        for (SkuBase sku : loadSkus()) {
            if (!"ON_SALE".equals(sku.productStatus) && !"SOLD_OUT".equals(sku.productStatus)) {
                continue;
            }
            int available = sku.stock - sku.reserved;
            int inbound = pending.getOrDefault(sku.skuId, 0);
            Sales s = sales.getOrDefault(sku.skuId, Sales.NONE);
            BigDecimal daily = BigDecimal.valueOf(s.windowQty).divide(BigDecimal.valueOf(window), 3, RoundingMode.HALF_UP);
            Object[] supplier = suppliers.get(sku.skuId);
            Long supplierId = supplier == null ? null : lng(supplier[1]);
            int lead = defaultLead;
            if (supplier != null && supplier[3] != null) {
                lead = ((Number) supplier[3]).intValue();
            } else if (supplierId != null && averageLead.get(supplierId) != null) {
                lead = averageLead.get(supplierId).setScale(0, RoundingMode.CEILING).intValue();
            }
            int safetyStock = daily.multiply(BigDecimal.valueOf(safetyDays)).setScale(0, RoundingMode.CEILING).intValue();
            int reorderLevel = daily.multiply(BigDecimal.valueOf(lead)).setScale(0, RoundingMode.CEILING).intValue()
                    + safetyStock;
            BigDecimal cover = daily.signum() == 0 ? null
                    : BigDecimal.valueOf(Math.max(available, 0)).divide(daily, 1, RoundingMode.HALF_UP);

            List<String> reasons = new ArrayList<>();
            if (available <= sku.reorderPoint) {
                reasons.add("가용재고 " + available + "개 ≤ 재주문점 " + sku.reorderPoint + "개");
            }
            if (daily.signum() > 0 && available + inbound < reorderLevel) {
                reasons.add("최근 " + window + "일 판매 " + s.windowQty + "개(일 " + daily.stripTrailingZeros().toPlainString()
                        + "개) 기준, 리드타임 " + lead + "일+안전재고 " + safetyDays + "일 필요량 " + reorderLevel
                        + "개 > 가용+입고예정 " + (available + inbound) + "개");
            }
            if (cover != null && cover.compareTo(BigDecimal.valueOf(coverTarget)) < 0 && inbound == 0) {
                reasons.add("재고 소진 예상 " + cover.toPlainString() + "일 < 기준 " + coverTarget + "일");
            }
            if (reasons.isEmpty()) {
                continue;
            }
            int target = daily.multiply(BigDecimal.valueOf(lead + safetyDays + coverTarget))
                    .setScale(0, RoundingMode.CEILING).intValue();
            int suggested = Math.max(Math.max(target, sku.reorderPoint + 1) - Math.max(available, 0) - inbound, 0);
            rows.add(new InventoryAnalyticsDtos.LowStockRow(sku.productId, sku.productName, sku.skuId, sku.skuCode,
                    sku.option(), available, sku.reorderPoint, inbound, s.windowQty, window, daily, cover, lead,
                    safetyStock, reorderLevel, suggested, supplierId, supplier == null ? null : (String) supplier[2],
                    reasons));
        }
        rows.sort(Comparator.comparing((InventoryAnalyticsDtos.LowStockRow r) -> r.coverDays() == null
                        ? BigDecimal.valueOf(Long.MAX_VALUE) : r.coverDays())
                .thenComparing(InventoryAnalyticsDtos.LowStockRow::availableQuantity));
        List<String> criteria = List.of(
                "고정 기준: 가용재고(재고−예약) ≤ SKU 재주문점",
                "판매속도 기준: 최근 " + window + "일 판매량 ÷ " + window + " = 일평균 판매",
                "필요량 = 일평균 × (리드타임 + 안전재고 " + safetyDays + "일), 입고예정 수량 반영",
                "리드타임: 거래처 설정값 → 과거 입고 평균 → 기본 " + defaultLead + "일",
                "권장 발주량은 참고용이며 자동 발주하지 않습니다.");
        return new InventoryAnalyticsDtos.AnalysisResponse<>(today, window, criteria, rows);
    }

    @Transactional(readOnly = true)
    public InventoryAnalyticsDtos.AnalysisResponse<InventoryAnalyticsDtos.SlowMovingRow> slowMoving(Integer days) {
        PricingPolicy policy = policyService.current();
        int period = days != null && days > 0 ? Math.min(days, 365) : positive(policy.getSlowMovingDays(), 30);
        int maxSales = policy.getSlowMovingMaxSales() == null ? 2 : policy.getSlowMovingMaxSales();
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        Map<Long, Sales> sales = sales(today.minusDays(period - 1L));
        Map<Long, LocalDate> lastReceived = lastReceived();
        List<InventoryAnalyticsDtos.SlowMovingRow> rows = new ArrayList<>();
        for (SkuBase sku : loadSkus()) {
            if (sku.stock <= 0) {
                continue;
            }
            Sales s = sales.getOrDefault(sku.skuId, Sales.NONE);
            if (s.windowQty > maxSales) {
                continue;
            }
            LocalDate received = lastReceived.get(sku.skuId);
            if (received != null && received.isAfter(today.minusDays(period))) {
                continue;
            }
            Integer sinceSale = s.lastSold == null ? null : (int) ChronoUnit.DAYS.between(s.lastSold, today);
            BigDecimal value = sku.unitCost == null ? null
                    : sku.unitCost.multiply(BigDecimal.valueOf(sku.stock)).setScale(0, RoundingMode.HALF_UP);
            String reason = "최근 " + period + "일 판매 " + s.windowQty + "개(기준 " + maxSales + "개 이하), 재고 " + sku.stock
                    + "개" + (s.lastSold == null ? ", 판매 이력 없음" : ", 마지막 판매 " + sinceSale + "일 전");
            rows.add(new InventoryAnalyticsDtos.SlowMovingRow(sku.productId, sku.productName, sku.skuId, sku.skuCode,
                    sku.option(), sku.stock, s.windowQty, period, s.lastSold, received, sinceSale, value, reason));
        }
        rows.sort(Comparator.comparing((InventoryAnalyticsDtos.SlowMovingRow r) -> r.stockValue() == null
                ? BigDecimal.ZERO : r.stockValue()).reversed());
        List<String> criteria = List.of(
                "재고가 있고 최근 " + period + "일 판매수량이 " + maxSales + "개 이하인 SKU",
                "최근 " + period + "일 이내에 입고된 SKU는 제외",
                "판매수량은 결제 승인된 주문 중 취소·반품을 제외한 수량");
        return new InventoryAnalyticsDtos.AnalysisResponse<>(today, period, criteria, rows);
    }

    private List<SkuBase> loadSkus() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT s.sku_id, s.sku_code, s.color, s.size, p.product_id, p.product_name, p.status,
                       p.purchase_price, COALESCE(i.stock_quantity, 0), COALESCE(i.reserved_quantity, 0),
                       COALESCE(i.reorder_point, 5), sc.avg_unit_cost
                FROM product_sku s
                JOIN product p ON p.product_id = s.product_id
                LEFT JOIN inventory i ON i.sku_id = s.sku_id
                LEFT JOIN sku_cost sc ON sc.sku_id = s.sku_id
                WHERE p.status <> 'DISCONTINUED' AND s.status <> 'DISCONTINUED'
                ORDER BY p.product_id, s.sku_id
                """).getResultList();
        List<SkuBase> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            SkuBase sku = new SkuBase();
            sku.skuId = lng(r[0]);
            sku.skuCode = (String) r[1];
            sku.color = (String) r[2];
            sku.size = (String) r[3];
            sku.productId = lng(r[4]);
            sku.productName = (String) r[5];
            sku.productStatus = (String) r[6];
            BigDecimal standard = dec(r[7]);
            sku.stock = ((Number) r[8]).intValue();
            sku.reserved = ((Number) r[9]).intValue();
            sku.reorderPoint = ((Number) r[10]).intValue();
            BigDecimal average = dec(r[11]);
            if (average != null) {
                sku.unitCost = average;
                sku.costSource = "WEIGHTED_AVERAGE";
            } else if (standard != null) {
                sku.unitCost = standard;
                sku.costSource = "STANDARD";
            } else {
                sku.costSource = "UNKNOWN";
            }
            result.add(sku);
        }
        return result;
    }

    /** Net sold quantity per SKU since {@code from} (paid orders, cancelled/returned lines excluded). */
    private Map<Long, Sales> sales(LocalDate from) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT oi.sku_id,
                       COALESCE(SUM(CASE WHEN pay.approved_at >= :start THEN oi.quantity ELSE 0 END), 0),
                       MAX(pay.approved_at)
                FROM order_item oi
                JOIN (SELECT order_id, MIN(approved_at) AS approved_at FROM payment
                      WHERE approved_at IS NOT NULL GROUP BY order_id) pay ON pay.order_id = oi.order_id
                WHERE oi.status = 'ORDERED'
                GROUP BY oi.sku_id
                """).setParameter("start", Timestamp.from(from.atStartOfDay(ZONE).toInstant())).getResultList();
        Map<Long, Sales> result = new HashMap<>();
        for (Object[] r : rows) {
            Instant last = instant(r[2]);
            result.put(lng(r[0]), new Sales(((Number) r[1]).intValue(),
                    last == null ? null : last.atZone(ZONE).toLocalDate()));
        }
        return result;
    }

    private Map<Long, LocalDate> lastReceived() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT sku_id, MAX(created_at) FROM inventory_movement
                WHERE movement_type = 'RECEIPT' GROUP BY sku_id
                """).getResultList();
        Map<Long, LocalDate> result = new HashMap<>();
        for (Object[] r : rows) {
            Instant at = instant(r[1]);
            if (at != null) {
                result.put(lng(r[0]), at.atZone(ZONE).toLocalDate());
            }
        }
        return result;
    }

    private Map<Long, Object[]> latestSupplierBySku() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT DISTINCT ON (h.sku_id) h.sku_id, h.supplier_id, sp.supplier_name, sp.lead_days
                FROM product_cost_history h
                LEFT JOIN supplier sp ON sp.supplier_id = h.supplier_id
                WHERE h.sku_id IS NOT NULL
                ORDER BY h.sku_id, h.received_at DESC
                """).getResultList();
        Map<Long, Object[]> result = new LinkedHashMap<>();
        for (Object[] r : rows) {
            result.put(lng(r[0]), r);
        }
        return result;
    }

    public Map<Long, Integer> pendingInbound() {
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] r : receiptItemRepository.pendingQuantityBySku()) {
            result.put(lng(r[0]), ((Number) r[1]).intValue());
        }
        return result;
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    static Long lng(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    static BigDecimal dec(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return Instant.parse(value.toString());
    }

    private record Sales(int windowQty, LocalDate lastSold) {
        static final Sales NONE = new Sales(0, null);
    }

    private static final class SkuBase {
        Long skuId;
        String skuCode;
        String color;
        String size;
        Long productId;
        String productName;
        String productStatus;
        int stock;
        int reserved;
        int reorderPoint;
        BigDecimal unitCost;
        String costSource;

        String option() {
            return color + "/" + size;
        }
    }
}
