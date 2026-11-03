package com.petitcamel.shop.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InventoryAnalyticsDtos {

    private InventoryAnalyticsDtos() {
    }

    public record TransactionRow(
            Long id,
            Instant createdAt,
            String movementType,
            String movementLabel,
            Long productId,
            String productName,
            Long skuId,
            String skuCode,
            String optionName,
            int quantity,
            Integer stockBefore,
            Integer stockAfter,
            String referenceType,
            String referenceId,
            Long purchaseReceiptId,
            String receiptNo,
            String managementNo,
            BigDecimal unitCost,
            String reason,
            Long actorMemberId,
            String actorName
    ) {
    }

    public record StockRow(
            Long productId,
            String productName,
            String productStatus,
            Long skuId,
            String skuCode,
            String optionName,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            int reorderPoint,
            int pendingInbound,
            BigDecimal unitCost,
            String costSource,
            BigDecimal stockValue,
            String stockStatus
    ) {
    }

    public record StockStatusResponse(
            int skuCount,
            long totalQuantity,
            BigDecimal totalValue,
            int soldOutCount,
            int lowStockCount,
            int unknownCostCount,
            List<StockRow> rows
    ) {
    }

    public record LowStockRow(
            Long productId,
            String productName,
            Long skuId,
            String skuCode,
            String optionName,
            int availableQuantity,
            int reorderPoint,
            int pendingInbound,
            int salesInWindow,
            int windowDays,
            BigDecimal dailySales,
            BigDecimal coverDays,
            int leadDays,
            int safetyStock,
            int reorderLevel,
            int suggestedQuantity,
            Long supplierId,
            String supplierName,
            List<String> reasons
    ) {
    }

    public record SlowMovingRow(
            Long productId,
            String productName,
            Long skuId,
            String skuCode,
            String optionName,
            int stockQuantity,
            int salesInPeriod,
            int periodDays,
            LocalDate lastSoldDate,
            LocalDate lastReceivedDate,
            Integer daysSinceLastSale,
            BigDecimal stockValue,
            String reason
    ) {
    }

    public record AnalysisResponse<T>(LocalDate asOf, int windowDays, List<String> criteria, List<T> rows) {
    }
}
