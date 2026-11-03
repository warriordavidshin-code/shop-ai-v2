package com.petitcamel.shop.analytics.service;

import com.petitcamel.shop.analytics.dto.AnalyticsDtos;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.cost.service.ProductCostService;
import com.petitcamel.shop.pricing.domain.PricingPolicy;
import com.petitcamel.shop.pricing.service.PriceCalculator;
import com.petitcamel.shop.pricing.service.PricingPolicyService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 손익 분석. Sales are recognised on payment approval (Asia/Seoul) and cancellations/returns are netted against
 * the period of the original sale, so a period's figures don't shift when looked at again later.
 * <ul>
 *   <li>순매출 = 총판매 − 할인 − 환불(취소·반품)</li>
 *   <li>매출원가 = 판매 시점 원가 스냅샷 × 순판매수량 (+ 재입고되지 않은 반품 수량)</li>
 *   <li>공헌이익 = 매출총이익 + 배송비 수입 − 결제수수료 − 배송원가 − 기타 변동판매비</li>
 * </ul>
 * Order-level amounts (payment fee, shipping cost, delivery revenue) are allocated to lines by paid amount
 * exactly once per order, so product rows add up to the order totals.
 */
@Service
public class ProfitAnalyticsService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final EntityManager entityManager;
    private final PricingPolicyService policyService;
    private final ProductCostService costService;
    private final Clock clock;

    public ProfitAnalyticsService(EntityManager entityManager, PricingPolicyService policyService,
                                  ProductCostService costService, Clock clock) {
        this.entityManager = entityManager;
        this.policyService = policyService;
        this.costService = costService;
        this.clock = clock;
    }

    public AnalyticsDtos.Period resolvePeriod(String code, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        String period = code == null || code.isBlank() ? "this_month" : code;
        return switch (period) {
            case "today" -> new AnalyticsDtos.Period(period, today, today);
            case "7d" -> new AnalyticsDtos.Period(period, today.minusDays(6), today);
            case "this_month" -> new AnalyticsDtos.Period(period, today.withDayOfMonth(1), today);
            case "last_month" -> {
                YearMonth last = YearMonth.from(today).minusMonths(1);
                yield new AnalyticsDtos.Period(period, last.atDay(1), last.atEndOfMonth());
            }
            case "custom" -> {
                if (from == null || to == null || to.isBefore(from)) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "조회 시작일과 종료일을 올바르게 입력해 주세요.");
                }
                if (from.plusYears(3).isBefore(to)) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "조회 기간은 최대 3년입니다.");
                }
                yield new AnalyticsDtos.Period(period, from, to);
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "period 는 today, 7d, this_month, last_month, custom 중 하나여야 합니다.");
        };
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.ProductProfitResponse productProfit(AnalyticsDtos.Period period) {
        PricingPolicy policy = policyService.current();
        Computation computation = compute(period.from(), period.to(), policy);
        List<AnalyticsDtos.ProfitLine> lines = computation.byProduct.values().stream()
                .map(Acc::toLine)
                .sorted(Comparator.comparing(AnalyticsDtos.ProfitLine::netSales).reversed())
                .toList();
        return new AnalyticsDtos.ProductProfitResponse(period, revenueBasis(policy), computation.total.toTotals(),
                lines, notes(computation, policy));
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.MonthlyProfitResponse monthlyProfit(int year) {
        PricingPolicy policy = policyService.current();
        Map<String, BigDecimal[]> purchases = monthlyPurchases(year);
        List<AnalyticsDtos.MonthlyProfit> months = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int estimated = 0;
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            Computation c = compute(ym.atDay(1), ym.atEndOfMonth(), policy);
            estimated += c.total.estimatedQty;
            String key = ym.toString();
            BigDecimal[] purchase = purchases.getOrDefault(key, new BigDecimal[]{ZERO, ZERO});
            months.add(new AnalyticsDtos.MonthlyProfit(key, purchase[0], purchase[1], scale(c.total.netSales),
                    scale(c.total.cogs), scale(c.total.grossProfit()), rate(c.total.grossProfit(), c.total.netSales),
                    scale(c.total.contribution()), c.total.soldQty));
        }
        if (estimated > 0) {
            notes.add("원가 스냅샷이 없는 기존 주문 " + estimated + "개는 현재 원가로 추정했습니다.");
        }
        notes.add("매입액·지급액은 매입장부(구매일/지급일 기준)이며 재고자산 평가액과 다릅니다.");
        notes.add("영업이익은 고정비(임대료·인건비 등) 데이터가 없어 산출하지 않았습니다. 공헌이익과 구분해서 보세요.");
        return new AnalyticsDtos.MonthlyProfitResponse(year, revenueBasis(policy), months, notes);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.CategorySales> categorySales(AnalyticsDtos.Period period) {
        Computation computation = compute(period.from(), period.to(), policyService.current());
        Map<Long, Acc> byCategory = new LinkedHashMap<>();
        for (Acc product : computation.byProduct.values()) {
            Acc acc = byCategory.computeIfAbsent(product.categoryId == null ? -1L : product.categoryId, k -> {
                Acc created = new Acc();
                created.categoryId = product.categoryId;
                created.categoryName = product.categoryName == null ? "미분류" : product.categoryName;
                return created;
            });
            acc.merge(product);
        }
        BigDecimal total = computation.total.netSales;
        return byCategory.values().stream()
                .sorted(Comparator.comparing((Acc a) -> a.netSales).reversed())
                .map(a -> new AnalyticsDtos.CategorySales(a.categoryId, a.categoryName, scale(a.netSales),
                        scale(a.grossProfit()), a.soldQty, rate(a.netSales, total)))
                .toList();
    }

    /** Totals for an arbitrary date range; used by the dashboard. */
    @Transactional(readOnly = true)
    public AnalyticsDtos.ProfitTotals totals(LocalDate from, LocalDate to) {
        return compute(from, to, policyService.current()).total.toTotals();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.ProfitLine> productRanking(LocalDate from, LocalDate to, int limit) {
        return compute(from, to, policyService.current()).byProduct.values().stream()
                .map(Acc::toLine)
                .sorted(Comparator.comparing(AnalyticsDtos.ProfitLine::netSales).reversed())
                .limit(limit)
                .toList();
    }

    private Computation compute(LocalDate from, LocalDate to, PricingPolicy policy) {
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT oi.order_id, oi.product_id, oi.sku_id, oi.product_name, oi.quantity, oi.unit_price,
                       oi.discount_price, oi.payment_price, oi.status, oi.unit_cost,
                       o.order_status, o.delivery_amount, o.payment_amount,
                       pay.refunded, COALESCE(rr.restocked, TRUE), pr.category_id, c.name
                FROM order_item oi
                JOIN orders o ON o.order_id = oi.order_id
                JOIN (SELECT order_id, MIN(approved_at) AS approved_at,
                             SUM(COALESCE(refunded_amount, 0)) AS refunded
                      FROM payment WHERE approved_at IS NOT NULL GROUP BY order_id) pay ON pay.order_id = o.order_id
                LEFT JOIN return_request rr ON rr.order_id = o.order_id AND rr.status = 'COMPLETED'
                LEFT JOIN product pr ON pr.product_id = oi.product_id
                LEFT JOIN category c ON c.category_id = pr.category_id
                WHERE pay.approved_at >= :start AND pay.approved_at < :end
                ORDER BY oi.order_id, oi.order_item_id
                """)
                .setParameter("start", Timestamp.from(start))
                .setParameter("end", Timestamp.from(end))
                .getResultList();

        PriceCalculator.Basis basis = PricingPolicyService.basisOf(policy);
        BigDecimal divisor = basis.excludeVat() ? BigDecimal.ONE.add(basis.vatRate()) : BigDecimal.ONE;
        BigDecimal feeRate = nz(policy.getPaymentFeeRate());
        BigDecimal otherRate = nz(policy.getOtherSellingCostRate());
        BigDecimal shippingPerOrder = BigDecimal.valueOf(policy.getShippingCostPerOrder() == null ? 0
                : policy.getShippingCostPerOrder());

        Map<Long, List<Object[]>> byOrder = new LinkedHashMap<>();
        for (Object[] row : rows) {
            byOrder.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>()).add(row);
        }
        Computation result = new Computation();
        Map<Long, BigDecimal> estimatedCosts = new HashMap<>();
        for (List<Object[]> orderRows : byOrder.values()) {
            Object[] head = orderRows.get(0);
            String orderStatus = (String) head[10];
            boolean cancelled = "CANCELLED".equals(orderStatus);
            boolean returned = "RETURNED".equals(orderStatus);
            BigDecimal deliveryAmount = dec(head[11]);
            BigDecimal paymentAmount = dec(head[12]);
            BigDecimal refunded = dec(head[13]);
            boolean restocked = head[14] == null || (Boolean) head[14];

            BigDecimal orderPaid = ZERO;
            for (Object[] row : orderRows) {
                orderPaid = orderPaid.add(dec(row[7]));
            }
            BigDecimal orderFee = cancelled ? ZERO : paymentAmount.subtract(refunded).max(ZERO).multiply(feeRate);
            BigDecimal orderShipping = cancelled ? ZERO : shippingPerOrder;
            BigDecimal orderDeliveryRevenue = cancelled || returned ? ZERO : deliveryAmount.divide(divisor, 4,
                    RoundingMode.HALF_UP);
            result.total.orderCount++;

            for (Object[] row : orderRows) {
                Long productId = ((Number) row[1]).longValue();
                Long skuId = ((Number) row[2]).longValue();
                int qty = ((Number) row[4]).intValue();
                BigDecimal unitPrice = dec(row[5]);
                BigDecimal discount = dec(row[6]);
                BigDecimal paid = dec(row[7]);
                String itemStatus = (String) row[8];
                boolean refundedLine = "CANCELLED".equals(itemStatus) || "RETURNED".equals(itemStatus);
                BigDecimal share = orderPaid.signum() == 0 ? BigDecimal.valueOf(1.0 / orderRows.size())
                        : paid.divide(orderPaid, 10, RoundingMode.HALF_UP);

                BigDecimal unitCost = row[9] == null ? null : dec(row[9]);
                boolean estimated = false;
                if (unitCost == null) {
                    unitCost = estimatedCosts.computeIfAbsent(skuId, id -> {
                        BigDecimal c = costService.costForSale(productId, id, policy).unitCost();
                        return c == null ? ZERO : c;
                    });
                    estimated = true;
                }
                int soldQty = refundedLine ? 0 : qty;
                int costQty = soldQty + ("RETURNED".equals(itemStatus) && !restocked ? qty : 0);

                Acc acc = result.byProduct.computeIfAbsent(productId, k -> {
                    Acc created = new Acc();
                    created.productId = productId;
                    created.productName = (String) row[3];
                    created.categoryId = row[15] == null ? null : ((Number) row[15]).longValue();
                    created.categoryName = (String) row[16];
                    return created;
                });
                Acc line = new Acc();
                line.soldQty = soldQty;
                line.refundedQty = refundedLine ? qty : 0;
                line.grossSales = unitPrice.multiply(BigDecimal.valueOf(qty)).divide(divisor, 4, RoundingMode.HALF_UP);
                line.discount = discount.divide(divisor, 4, RoundingMode.HALF_UP);
                line.refund = refundedLine ? paid.divide(divisor, 4, RoundingMode.HALF_UP) : ZERO;
                line.netSales = line.grossSales.subtract(line.discount).subtract(line.refund);
                line.cogs = unitCost.multiply(BigDecimal.valueOf(costQty));
                line.paymentFee = orderFee.multiply(share);
                line.shippingCost = orderShipping.multiply(share);
                line.deliveryRevenue = orderDeliveryRevenue.multiply(share);
                line.otherCost = line.netSales.max(ZERO).multiply(otherRate);
                line.estimatedQty = estimated ? costQty : 0;
                acc.merge(line);
                result.total.merge(line);
            }
        }
        return result;
    }

    private Map<String, BigDecimal[]> monthlyPurchases(int year) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT to_char(purchase_date, 'YYYY-MM') AS ym, COALESCE(SUM(purchase_amount), 0),
                       COALESCE(SUM(payment_amount), 0)
                FROM purchase_ledger
                WHERE deleted_at IS NULL AND EXTRACT(YEAR FROM purchase_date) = :year
                GROUP BY ym
                """).setParameter("year", year).getResultList();
        Map<String, BigDecimal[]> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], new BigDecimal[]{dec(row[1]), dec(row[2])});
        }
        return result;
    }

    private static List<String> notes(Computation computation, PricingPolicy policy) {
        List<String> notes = new ArrayList<>();
        notes.add("매출 인식: 결제 승인일 기준. 취소·반품은 원 판매 기간에서 차감합니다.");
        notes.add("결제수수료 " + percent(policy.getPaymentFeeRate()) + ", 주문당 배송원가 "
                + (policy.getShippingCostPerOrder() == null ? 0 : policy.getShippingCostPerOrder())
                + "원은 정책 설정값이며 주문 단위로 한 번만 반영됩니다.");
        if (computation.total.estimatedQty > 0) {
            notes.add("원가 스냅샷이 없는 기존 주문 " + computation.total.estimatedQty + "개는 현재 원가로 추정했습니다.");
        }
        notes.add("공헌이익은 고정비 차감 전 금액이며 영업이익이 아닙니다.");
        return notes;
    }

    private static String revenueBasis(PricingPolicy policy) {
        return PricingPolicyService.basisOf(policy).excludeVat() ? "VAT_EXCLUDED" : "VAT_INCLUDED";
    }

    private static String percent(BigDecimal rate) {
        return rate == null ? "0%" : rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    static BigDecimal scale(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal dec(Object value) {
        if (value == null) {
            return ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static final class Computation {
        final Acc total = new Acc();
        final Map<Long, Acc> byProduct = new LinkedHashMap<>();
    }

    private static final class Acc {
        Long productId;
        String productName;
        Long categoryId;
        String categoryName;
        int orderCount;
        int soldQty;
        int refundedQty;
        int estimatedQty;
        BigDecimal grossSales = ZERO;
        BigDecimal discount = ZERO;
        BigDecimal refund = ZERO;
        BigDecimal netSales = ZERO;
        BigDecimal cogs = ZERO;
        BigDecimal paymentFee = ZERO;
        BigDecimal shippingCost = ZERO;
        BigDecimal deliveryRevenue = ZERO;
        BigDecimal otherCost = ZERO;

        void merge(Acc o) {
            soldQty += o.soldQty;
            refundedQty += o.refundedQty;
            estimatedQty += o.estimatedQty;
            grossSales = grossSales.add(o.grossSales);
            discount = discount.add(o.discount);
            refund = refund.add(o.refund);
            netSales = netSales.add(o.netSales);
            cogs = cogs.add(o.cogs);
            paymentFee = paymentFee.add(o.paymentFee);
            shippingCost = shippingCost.add(o.shippingCost);
            deliveryRevenue = deliveryRevenue.add(o.deliveryRevenue);
            otherCost = otherCost.add(o.otherCost);
        }

        BigDecimal grossProfit() {
            return netSales.subtract(cogs);
        }

        BigDecimal contribution() {
            return grossProfit().add(deliveryRevenue).subtract(paymentFee).subtract(shippingCost).subtract(otherCost);
        }

        AnalyticsDtos.ProfitLine toLine() {
            return new AnalyticsDtos.ProfitLine(productId, productName, categoryId, categoryName, soldQty, refundedQty,
                    scale(grossSales), scale(discount), scale(refund), scale(netSales), scale(cogs),
                    scale(grossProfit()), rate(grossProfit(), netSales), scale(paymentFee), scale(shippingCost),
                    scale(deliveryRevenue), scale(otherCost), scale(contribution()), rate(contribution(), netSales),
                    estimatedQty);
        }

        AnalyticsDtos.ProfitTotals toTotals() {
            return new AnalyticsDtos.ProfitTotals(orderCount, soldQty, refundedQty, scale(grossSales), scale(discount),
                    scale(refund), scale(netSales), scale(cogs), scale(grossProfit()), rate(grossProfit(), netSales),
                    scale(paymentFee), scale(shippingCost), scale(deliveryRevenue), scale(otherCost),
                    scale(contribution()), rate(contribution(), netSales), estimatedQty);
        }
    }
}
