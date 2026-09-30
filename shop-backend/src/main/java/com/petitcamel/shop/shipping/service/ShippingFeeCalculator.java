package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.shipping.domain.ExtraAreaType;
import com.petitcamel.shop.shipping.dto.ShippingQuote;

import java.math.BigDecimal;

/**
 * Delivery fee rule (whole won): base fee unless the (discounted) product amount reaches the free-shipping
 * threshold, plus a Jeju / remote-island surcharge that applies regardless of free shipping.
 */
public final class ShippingFeeCalculator {

    private ShippingFeeCalculator() {
    }

    public record Policy(
            long baseShippingFee,
            long freeShippingThreshold,
            long jejuExtraFee,
            long remoteAreaExtraFee
    ) {
    }

    public static ShippingQuote quote(Policy policy, BigDecimal productAmount, ExtraAreaType areaType) {
        return quote(policy, productAmount, areaType, null);
    }

    /** @param areaFee surcharge of the matched area; null uses the policy fee for {@code areaType} */
    public static ShippingQuote quote(Policy policy, BigDecimal productAmount, ExtraAreaType areaType, Long areaFee) {
        BigDecimal threshold = BigDecimal.valueOf(policy.freeShippingThreshold());
        if (productAmount == null || productAmount.signum() <= 0) {
            return new ShippingQuote(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    false, areaType, threshold);
        }
        boolean free = productAmount.compareTo(threshold) >= 0;
        long base = free ? 0 : policy.baseShippingFee();
        long extra = areaType == null ? 0
                : areaFee != null ? areaFee
                : areaType == ExtraAreaType.JEJU ? policy.jejuExtraFee() : policy.remoteAreaExtraFee();
        return new ShippingQuote(productAmount, BigDecimal.valueOf(base), BigDecimal.valueOf(extra),
                BigDecimal.valueOf(base + extra), free, areaType, threshold);
    }
}
