package com.petitcamel.shop.purchase.dto;

import com.petitcamel.shop.purchase.domain.PurchaseEnums.EntrySource;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceAiStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceCategory;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceType;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.KeptStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.MatchStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.PaymentMethod;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    public record ItemRequest(
            Long id,
            @NotBlank(message = "상품명을 입력해 주세요.") @Size(max = 200) String productName,
            @Size(max = 200) String optionName,
            @Size(max = 50) String color,
            @Size(max = 30) String size,
            @Size(max = 100) String supplierProductCode,
            @Min(0) @Max(1_000_000) Integer quantity,
            @Min(0) Long unitPrice,
            @Min(0) Long supplyAmount,
            @Min(0) Long taxAmount,
            @Min(0) Long totalAmount
    ) {
    }

    public record LedgerRequest(
            @NotNull(message = "매입일을 입력해 주세요.") LocalDate purchaseDate,
            Long supplierId,
            @Size(max = 200) String supplierName,
            @Size(max = 20) String supplierBusinessNo,
            @Size(max = 4000) String itemSummary,
            @Min(0) Long purchaseAmount,
            @Min(0) Long supplyAmount,
            @Min(0) Long taxAmount,
            @Min(0) Long paymentAmount,
            LocalDate paymentDate,
            PaymentMethod paymentMethod,
            @Size(max = 100) String recipientName,
            @Size(max = 100) String transferReference,
            KeptStatus statementKept,
            KeptStatus transferKept,
            EvidenceType evidenceType,
            @Size(max = 500) String evidenceLocation,
            @Size(max = 4000) String remarks,
            @Valid @Size(max = 200) List<ItemRequest> items,
            @Size(max = 30) List<Long> evidenceIds,
            Long sourceAnalysisId,
            boolean confirmDuplicate,
            Long version
    ) {
    }

    public record ItemResponse(
            Long id,
            int lineNo,
            String productName,
            String optionName,
            String color,
            String size,
            String supplierProductCode,
            Integer quantity,
            Long unitPrice,
            Long supplyAmount,
            Long taxAmount,
            Long totalAmount,
            MatchStatus matchStatus,
            Long matchedProductId,
            String matchedProductName,
            Long matchedSkuId,
            String matchedOptionName,
            BigDecimal matchScore,
            String matchNote,
            int receivedQuantity,
            int remainingQuantity
    ) {
    }

    public record EvidenceResponse(
            Long id,
            Long purchaseLedgerId,
            String managementNo,
            String originalFileName,
            String contentType,
            long fileSize,
            String fileHash,
            EvidenceCategory category,
            String categoryLabel,
            EvidenceAiStatus aiAnalysisStatus,
            Instant createdAt,
            String fileUrl
    ) {
    }

    public record LedgerRow(
            Long id,
            String managementNo,
            LocalDate purchaseDate,
            Long supplierId,
            String supplierName,
            String supplierBusinessNo,
            String itemSummary,
            Long purchaseAmount,
            Long supplyAmount,
            Long taxAmount,
            Long paymentAmount,
            LocalDate paymentDate,
            PaymentMethod paymentMethod,
            String recipientName,
            String transferReference,
            Long differenceAmount,
            KeptStatus statementKept,
            KeptStatus transferKept,
            EvidenceType evidenceType,
            String evidenceLocation,
            String remarks,
            VerificationStatus verificationStatus,
            String verificationLabel,
            List<String> missingFields,
            EntrySource entrySource,
            int evidenceCount,
            int itemCount,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    public record ReceiptBrief(Long id, String receiptNo, String status, Instant confirmedAt) {
    }

    public record LedgerDetail(
            LedgerRow ledger,
            List<ItemResponse> items,
            List<EvidenceResponse> evidences,
            List<ReceiptBrief> receipts,
            Long sourceAnalysisId
    ) {
    }

    public record DuplicateCheckRequest(
            Long excludeId,
            String supplierName,
            LocalDate purchaseDate,
            Long purchaseAmount,
            Long paymentAmount,
            String transferReference,
            List<Long> evidenceIds
    ) {
    }

    public record DuplicateCandidate(
            Long ledgerId,
            String managementNo,
            LocalDate purchaseDate,
            String supplierName,
            Long purchaseAmount,
            List<String> reasons
    ) {
    }

    public record MonthSummary(
            int month,
            long purchaseAmount,
            long paymentAmount,
            long purchaseCount,
            long statementKeptCount,
            long transferKeptCount,
            long differenceAmount,
            long needsReviewCount,
            long missingEvidenceCount
    ) {
    }

    public record SupplierTotal(
            String supplierName,
            long purchaseCount,
            long purchaseAmount,
            long paymentAmount
    ) {
    }

    public record SummaryResponse(
            int year,
            List<MonthSummary> months,
            MonthSummary total,
            List<SupplierTotal> suppliers,
            long unlinkedEvidenceCount
    ) {
    }

    public record EvidenceCategoryRequest(@NotNull EvidenceCategory category) {
    }
}
