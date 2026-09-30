package com.petitcamel.shop.shipping.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShippingPolicyUpdateRequest(
        @NotNull @Min(0) @Max(1_000_000) Long baseShippingFee,
        @NotNull @Min(0) @Max(100_000_000) Long freeShippingAmount,
        @NotNull @Min(0) @Max(1_000_000) Long jejuExtraFee,
        @NotNull @Min(0) @Max(1_000_000) Long remoteAreaExtraFee,
        @NotNull @Min(0) @Max(1_000_000) Long returnShippingFee,
        @NotNull @Min(0) @Max(1_000_000) Long exchangeShippingFee
) {
}
