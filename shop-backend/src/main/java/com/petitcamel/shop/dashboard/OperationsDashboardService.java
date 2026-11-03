package com.petitcamel.shop.dashboard;

import com.petitcamel.shop.ai.domain.AiAnalysisStatus;
import com.petitcamel.shop.ai.repository.AiAnalysisHistoryRepository;
import com.petitcamel.shop.analytics.dto.AnalyticsDtos;
import com.petitcamel.shop.analytics.service.ProfitAnalyticsService;
import com.petitcamel.shop.inventory.dto.InventoryAnalyticsDtos;
import com.petitcamel.shop.inventory.service.InventoryAnalyticsService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 통합 운영 대시보드. Figures come from the same services as the detail screens (profit analytics,
 * inventory analytics, purchase ledger), so the dashboard and the drill-down pages always agree.
 */
@Service
public class OperationsDashboardService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public record PurchaseCard(long purchaseAmount, long paymentAmount, long unpaidThisMonth, long unpaidTotal,
                               long previousPurchaseAmount, BigDecimal changeRate) {
    }

    public record StockCard(long totalQuantity, BigDecimal stockValue, int soldOutCount, int lowStockCount,
                            int slowMovingCount, int unknownCostCount, int pendingReceiptCount) {
    }

    public record SalesCard(BigDecimal netSales, int soldQuantity, BigDecimal grossProfit, BigDecimal grossMarginRate,
                            BigDecimal contributionProfit, BigDecimal previousNetSales, BigDecimal netSalesChangeRate,
                            BigDecimal previousGrossProfit, int orderCount) {
    }

    public record AiCard(long callsThisMonth, long failuresThisMonth, long inputTokens, long outputTokens,
                         BigDecimal estimatedCostUsd, boolean available, boolean mockMode) {
    }

    public record NamedValue(String name, BigDecimal value) {
    }

    public record LowStockItem(Long skuId, String productName, String optionName, int available, int suggestedQuantity,
                               BigDecimal coverDays) {
    }

    public record DashboardResponse(
            LocalDate asOf,
            String month,
            PurchaseCard purchases,
            StockCard stock,
            SalesCard sales,
            AiCard ai,
            List<AnalyticsDtos.MonthlyProfit> monthlyTrend,
            List<AnalyticsDtos.CategorySales> categoryShare,
            List<AnalyticsDtos.ProfitLine> productRanking,
            List<NamedValue> supplierPurchases,
            List<LowStockItem> lowStockItems,
            List<String> notes
    ) {
    }

    private final ProfitAnalyticsService profitService;
    private final InventoryAnalyticsService inventoryService;
    private final AiAnalysisHistoryRepository aiHistoryRepository;
    private final com.petitcamel.shop.ai.config.AiAnalysisProperties aiProperties;
    private final EntityManager entityManager;
    private final Clock clock;

    public OperationsDashboardService(ProfitAnalyticsService profitService, InventoryAnalyticsService inventoryService,
                                      AiAnalysisHistoryRepository aiHistoryRepository,
                                      com.petitcamel.shop.ai.config.AiAnalysisProperties aiProperties,
                                      EntityManager entityManager, Clock clock) {
        this.profitService = profitService;
        this.inventoryService = inventoryService;
        this.aiHistoryRepository = aiHistoryRepository;
        this.aiProperties = aiProperties;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse operations() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        YearMonth month = YearMonth.from(today);
        YearMonth previous = month.minusMonths(1);
        LocalDate monthStart = month.atDay(1);
        // compare month-to-date with the same span of the previous month
        LocalDate prevEnd = previous.atDay(Math.min(today.getDayOfMonth(), previous.lengthOfMonth()));

        long[] purchaseNow = purchaseTotals(monthStart, today);
        long[] purchasePrev = purchaseTotals(previous.atDay(1), prevEnd);
        Number unpaidTotal = (Number) entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(GREATEST(COALESCE(purchase_amount, 0) - COALESCE(payment_amount, 0), 0)), 0)
                FROM purchase_ledger WHERE deleted_at IS NULL
                """).getSingleResult();
        PurchaseCard purchases = new PurchaseCard(purchaseNow[0], purchaseNow[1], purchaseNow[2],
                unpaidTotal.longValue(), purchasePrev[0], change(BigDecimal.valueOf(purchaseNow[0]),
                BigDecimal.valueOf(purchasePrev[0])));

        InventoryAnalyticsDtos.StockStatusResponse status = inventoryService.status(null, null);
        var lowStock = inventoryService.lowStock();
        var slowMoving = inventoryService.slowMoving(null);
        Number pendingReceipts = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM purchase_receipt WHERE receipt_status = 'PENDING'").getSingleResult();
        StockCard stock = new StockCard(status.totalQuantity(), status.totalValue(), status.soldOutCount(),
                lowStock.rows().size(), slowMoving.rows().size(), status.unknownCostCount(),
                pendingReceipts.intValue());

        AnalyticsDtos.ProfitTotals now = profitService.totals(monthStart, today);
        AnalyticsDtos.ProfitTotals prev = profitService.totals(previous.atDay(1), prevEnd);
        SalesCard sales = new SalesCard(now.netSales(), now.soldQuantity(), now.grossProfit(), now.grossMarginRate(),
                now.contributionProfit(), prev.netSales(), change(now.netSales(), prev.netSales()), prev.grossProfit(),
                now.orderCount());

        var usage = aiHistoryRepository.usageSince(monthStart.atStartOfDay(ZONE).toInstant());
        long failures = aiHistoryRepository.countByCreatedAtAfterAndStatus(monthStart.atStartOfDay(ZONE).toInstant(),
                AiAnalysisStatus.FAILED);
        AiCard ai = new AiCard(num(usage == null ? null : usage.getCalls()), failures,
                num(usage == null ? null : usage.getInputTokens()), num(usage == null ? null : usage.getOutputTokens()),
                usage == null || usage.getEstimatedCostUsd() == null ? BigDecimal.ZERO
                        : new BigDecimal(usage.getEstimatedCostUsd().toString()).setScale(4, RoundingMode.HALF_UP),
                aiProperties.isAvailable(), aiProperties.isMockEnabled());

        List<AnalyticsDtos.MonthlyProfit> trend = new ArrayList<>();
        var thisYear = profitService.monthlyProfit(month.getYear()).months();
        if (month.getMonthValue() < 12) {
            var lastYear = profitService.monthlyProfit(month.getYear() - 1).months();
            trend.addAll(lastYear.subList(month.getMonthValue(), 12));
        }
        trend.addAll(thisYear.subList(0, month.getMonthValue()));

        AnalyticsDtos.Period monthPeriod = new AnalyticsDtos.Period("this_month", monthStart, today);
        List<AnalyticsDtos.CategorySales> categories = profitService.categorySales(monthPeriod);
        List<AnalyticsDtos.ProfitLine> ranking = profitService.productRanking(monthStart, today, 10);

        @SuppressWarnings("unchecked")
        List<Object[]> supplierRows = entityManager.createNativeQuery("""
                SELECT COALESCE(NULLIF(TRIM(supplier_name), ''), '(거래처 미입력)') AS name,
                       COALESCE(SUM(purchase_amount), 0) AS amount
                FROM purchase_ledger
                WHERE deleted_at IS NULL AND EXTRACT(YEAR FROM purchase_date) = :year
                GROUP BY name ORDER BY amount DESC LIMIT 10
                """).setParameter("year", month.getYear()).getResultList();
        List<NamedValue> suppliers = supplierRows.stream()
                .map(r -> new NamedValue((String) r[0], new BigDecimal(r[1].toString()))).toList();

        List<LowStockItem> lowItems = lowStock.rows().stream().limit(10)
                .map(r -> new LowStockItem(r.skuId(), r.productName(), r.optionName(), r.availableQuantity(),
                        r.suggestedQuantity(), r.coverDays()))
                .toList();

        List<String> notes = new ArrayList<>();
        notes.add("전월 대비는 이번 달 1일~오늘과 전월 같은 기간을 비교합니다.");
        notes.add("매입액은 매입장부 기준이며, 재고자산 평가액(이동평균 원가 × 재고)과 다릅니다.");
        if (status.unknownCostCount() > 0) {
            notes.add("원가 정보가 없는 SKU " + status.unknownCostCount() + "개는 재고자산 평가액에서 제외되었습니다.");
        }
        if (now.estimatedCostQuantity() > 0) {
            notes.add("원가 스냅샷이 없는 판매 " + now.estimatedCostQuantity() + "개는 현재 원가로 추정했습니다.");
        }
        return new DashboardResponse(today, month.toString(), purchases, stock, sales, ai, trend, categories, ranking,
                suppliers, lowItems, notes);
    }

    /** [purchase, payment, unpaid] for purchases dated in the range. */
    private long[] purchaseTotals(LocalDate from, LocalDate to) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(purchase_amount), 0), COALESCE(SUM(payment_amount), 0),
                       COALESCE(SUM(GREATEST(COALESCE(purchase_amount, 0) - COALESCE(payment_amount, 0), 0)), 0)
                FROM purchase_ledger
                WHERE deleted_at IS NULL AND purchase_date BETWEEN :from AND :to
                """).setParameter("from", from).setParameter("to", to).getSingleResult();
        return new long[]{((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue()};
    }

    private static BigDecimal change(BigDecimal now, BigDecimal before) {
        if (before == null || before.signum() == 0) {
            return null;
        }
        return now.subtract(before).divide(before.abs(), 4, RoundingMode.HALF_UP);
    }

    private static long num(Number n) {
        return n == null ? 0 : n.longValue();
    }
}
