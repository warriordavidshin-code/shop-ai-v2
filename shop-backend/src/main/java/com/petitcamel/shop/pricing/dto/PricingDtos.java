package com.petitcamel.shop.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PricingDtos {

    private PricingDtos() {
    }

    /** Rates are fractions (0.5 = 50%). */
    public record PolicyResponse(
            BigDecimal competitiveMarginRate,
            BigDecimal standardMarginRate,
            BigDecimal premiumMarginRate,
            BigDecimal minimumMarginRate,
            String roundingMode,
            String marginBasis,
            String taxType,
            BigDecimal vatRate,
            String costingMethod,
            BigDecimal paymentFeeRate,
            long shippingCostPerOrder,
            BigDecimal otherSellingCostRate,
            int lowStockCoverDays,
            int safetyStockDays,
            int defaultLeadDays,
            int salesWindowDays,
            int slowMovingDays,
            int slowMovingMaxSales,
            Instant updatedAt,
            Long version
    ) {
    }

    public record PolicyUpdateRequest(
            @NotNull @DecimalMin("0") @DecimalMax("0.95") BigDecimal competitiveMarginRate,
            @NotNull @DecimalMin("0") @DecimalMax("0.95") BigDecimal standardMarginRate,
            @NotNull @DecimalMin("0") @DecimalMax("0.95") BigDecimal premiumMarginRate,
            @NotNull @DecimalMin("0") @DecimalMax("0.95") BigDecimal minimumMarginRate,
            @NotBlank @Pattern(regexp = "NONE|UP_100|UP_1000|END_900|END_900_ROUND_9000") String roundingMode,
            @NotBlank @Pattern(regexp = "VAT_INCLUDED|VAT_EXCLUDED") String marginBasis,
            @NotBlank @Pattern(regexp = "GENERAL|SIMPLIFIED|EXEMPT") String taxType,
            @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal vatRate,
            @NotBlank @Pattern(regexp = "RECENT|WEIGHTED_AVERAGE|STANDARD") String costingMethod,
            @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal paymentFeeRate,
            @NotNull @Min(0) @Max(1_000_000) Long shippingCostPerOrder,
            @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal otherSellingCostRate,
            @NotNull @Min(0) @Max(365) Integer lowStockCoverDays,
            @NotNull @Min(0) @Max(365) Integer safetyStockDays,
            @NotNull @Min(0) @Max(365) Integer defaultLeadDays,
            @NotNull @Min(1) @Max(365) Integer salesWindowDays,
            @NotNull @Min(1) @Max(730) Integer slowMovingDays,
            @NotNull @Min(0) @Max(10_000) Integer slowMovingMaxSales,
            Long version
    ) {
    }

    public record RecommendRequest(
            @DecimalMin("0") BigDecimal unitCost,
            Long productId,
            @DecimalMin("0") BigDecimal currentSalePrice
    ) {
    }

    public record PriceOption(
            String tier,
            String label,
            BigDecimal targetMarginRate,
            BigDecimal rawPrice,
            long roundedPrice,
            BigDecimal marginRate,
            long grossProfit,
            boolean belowMinimum,
            String note
    ) {
    }

    public record CurrentPriceCheck(
            BigDecimal salePrice,
            BigDecimal marginRate,
            long grossProfit,
            boolean belowMinimum
    ) {
    }

    public record RecommendResponse(
            BigDecimal unitCost,
            String costSource,
            String marginBasis,
            String roundingMode,
            BigDecimal minimumMarginRate,
            List<PriceOption> options,
            CurrentPriceCheck current,
            List<String> warnings,
            String explanation
    ) {
    }

    public record PriceHistoryResponse(
            Long id,
            BigDecimal normalPriceBefore,
            BigDecimal normalPriceAfter,
            BigDecimal salePriceBefore,
            BigDecimal salePriceAfter,
            String reason,
            Long changedBy,
            Instant changedAt
    ) {
    }
}
