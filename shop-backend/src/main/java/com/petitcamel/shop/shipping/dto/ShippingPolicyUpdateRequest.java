package com.petitcamel.shop.shipping.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ShippingPolicyUpdateRequest(
        @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal baseShippingFee,
        @NotNull @DecimalMin("0") @DecimalMax("100000000") BigDecimal freeShippingAmount,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal jejuExtraFee,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal remoteAreaExtraFee,
        @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal returnShippingFee
) {
}
