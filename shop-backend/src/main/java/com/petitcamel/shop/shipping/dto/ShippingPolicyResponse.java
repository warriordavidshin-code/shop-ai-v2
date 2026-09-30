package com.petitcamel.shop.shipping.dto;

import java.time.Instant;

/** Fees in whole won. {@code freeShippingAmount} is the free-shipping threshold. */
public record ShippingPolicyResponse(
        Long policyId,
        String name,
        long baseShippingFee,
        long freeShippingAmount,
        long jejuExtraFee,
        long remoteAreaExtraFee,
        long returnShippingFee,
        long exchangeShippingFee,
        Instant updatedAt
) {
}
