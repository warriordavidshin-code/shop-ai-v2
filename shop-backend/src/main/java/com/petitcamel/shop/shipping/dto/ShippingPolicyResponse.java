package com.petitcamel.shop.shipping.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ShippingPolicyResponse(
        BigDecimal baseShippingFee,
        BigDecimal freeShippingAmount,
        BigDecimal jejuExtraFee,
        BigDecimal remoteAreaExtraFee,
        BigDecimal returnShippingFee,
        Instant updatedAt
) {
}
