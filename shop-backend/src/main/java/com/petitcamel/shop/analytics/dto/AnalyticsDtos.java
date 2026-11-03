package com.petitcamel.shop.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Period(String code, LocalDate from, LocalDate to) {
    }

    /** All amounts are KRW; revenue follows the pricing policy's margin basis (VAT included or excluded). */
    public record ProfitLine(
            Long productId,
            String productName,
            Long categoryId,
            String categoryName,
            int soldQuantity,
            int refundedQuantity,
            BigDecimal grossSales,
            BigDecimal discountAmount,
            BigDecimal refundAmount,
            BigDecimal netSales,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal grossMarginRate,
            BigDecimal paymentFee,
            BigDecimal shippingCost,
            BigDecimal deliveryRevenue,
            BigDecimal otherSellingCost,
            BigDecimal contributionProfit,
            BigDecimal contributionMarginRate,
            int estimatedCostQuantity
    ) {
    }

    public record ProfitTotals(
            int orderCount,
            int soldQuantity,
            int refundedQuantity,
            BigDecimal grossSales,
            BigDecimal discountAmount,
            BigDecimal refundAmount,
            BigDecimal netSales,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal grossMarginRate,
            BigDecimal paymentFee,
            BigDecimal shippingCost,
            BigDecimal deliveryRevenue,
            BigDecimal otherSellingCost,
            BigDecimal contributionProfit,
            BigDecimal contributionMarginRate,
            int estimatedCostQuantity
    ) {
    }

    public record ProductProfitResponse(
            Period period,
            String revenueBasis,
            ProfitTotals totals,
            List<ProfitLine> products,
            List<String> notes
    ) {
    }

    public record MonthlyProfit(
            String month,
            BigDecimal purchaseAmount,
            BigDecimal paymentAmount,
            BigDecimal netSales,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal grossMarginRate,
            BigDecimal contributionProfit,
            int soldQuantity
    ) {
    }

    public record MonthlyProfitResponse(int year, String revenueBasis, List<MonthlyProfit> months,
                                        List<String> notes) {
    }

    public record CategorySales(Long categoryId, String categoryName, BigDecimal netSales, BigDecimal grossProfit,
                                int soldQuantity, BigDecimal share) {
    }
}
