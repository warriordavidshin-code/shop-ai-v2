package com.petitcamel.shop.purchase.dto;

import com.petitcamel.shop.purchase.domain.PurchaseEnums.AllocationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReceivingDtos {

    private ReceivingDtos() {
    }

    public record PlanLine(@NotNull Long purchaseLedgerItemId, @NotNull @Min(0) @Max(1_000_000) Integer expectedQuantity) {
    }

    public record CreateReceiptRequest(
            @NotNull Long purchaseLedgerId,
            LocalDate expectedDate,
            @Min(0) Long freightCost,
            @Min(0) Long incidentalCost,
            @Min(0) Long packagingCost,
            @Min(0) Long otherCost,
            AllocationMethod allocationMethod,
            @Size(max = 1000) String memo,
            @Valid @Size(max = 200) List<PlanLine> items
    ) {
    }

    public record ConfirmLine(@NotNull Long receiptItemId, @NotNull @Min(0) @Max(1_000_000) Integer receivedQuantity) {
    }

    /** Additional costs can still be corrected at confirmation; null keeps the planned value. */
    public record ConfirmReceiptRequest(
            @Valid @Size(max = 200) List<ConfirmLine> items,
            @Min(0) Long freightCost,
            @Min(0) Long incidentalCost,
            @Min(0) Long packagingCost,
            @Min(0) Long otherCost,
            AllocationMethod allocationMethod,
            @Size(max = 1000) String memo
    ) {
    }

    public record ReceiptItemResponse(
            Long id,
            Long purchaseLedgerItemId,
            Long productId,
            String productName,
            Long skuId,
            String optionName,
            int orderedQuantity,
            int expectedQuantity,
            Integer receivedQuantity,
            int ledgerReceivedQuantity,
            int ledgerRemainingQuantity,
            long unitCost,
            BigDecimal allocatedCost,
            BigDecimal landedUnitCost,
            int currentStock
    ) {
    }

    public record ReceiptResponse(
            Long id,
            String receiptNo,
            Long purchaseLedgerId,
            String managementNo,
            String supplierName,
            String status,
            LocalDate expectedDate,
            long freightCost,
            long incidentalCost,
            long packagingCost,
            long otherCost,
            long additionalCostTotal,
            AllocationMethod allocationMethod,
            String memo,
            Instant confirmedAt,
            Long confirmedBy,
            Instant createdAt,
            List<ReceiptItemResponse> items,
            List<String> warnings,
            boolean alreadyConfirmed
    ) {
    }
}
