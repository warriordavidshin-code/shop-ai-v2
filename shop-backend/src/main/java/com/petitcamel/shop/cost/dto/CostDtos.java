package com.petitcamel.shop.cost.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CostDtos {

    private CostDtos() {
    }

    public record CostHistoryResponse(
            Long id,
            Long skuId,
            String optionName,
            Long purchaseReceiptItemId,
            Long purchaseLedgerId,
            Long supplierId,
            int quantity,
            BigDecimal unitCost,
            BigDecimal allocatedUnitCost,
            BigDecimal landedUnitCost,
            BigDecimal totalCost,
            BigDecimal avgCostBefore,
            BigDecimal avgCostAfter,
            Instant receivedAt
    ) {
    }

    public record SkuCostLine(
            Long skuId,
            String optionName,
            int stockQuantity,
            BigDecimal movingAverageCost,
            BigDecimal recentCost,
            BigDecimal appliedUnitCost,
            BigDecimal inventoryValue
    ) {
    }

    /**
     * {@code simpleWeightedAverageCost} = total confirmed purchase cost / total confirmed quantity (all time).
     * {@code movingAverageCost} is the stock-weighted moving average used for inventory valuation and COGS
     * when the costing method is WEIGHTED_AVERAGE. They differ once stock has been sold between receipts.
     */
    public record CostSummaryResponse(
            Long productId,
            String costingMethod,
            BigDecimal appliedUnitCost,
            String appliedCostSource,
            BigDecimal recentCost,
            BigDecimal movingAverageCost,
            BigDecimal simpleWeightedAverageCost,
            BigDecimal standardCost,
            long totalReceivedQuantity,
            BigDecimal totalReceivedCost,
            int receiptCount,
            long stockQuantity,
            BigDecimal inventoryValue,
            List<SkuCostLine> skus
    ) {
    }
}
