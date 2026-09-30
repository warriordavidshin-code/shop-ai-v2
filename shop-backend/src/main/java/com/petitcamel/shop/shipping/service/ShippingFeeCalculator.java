package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.shipping.domain.ExtraAreaType;
import com.petitcamel.shop.shipping.dto.ShippingQuote;

import java.math.BigDecimal;

/**
 * Delivery fee rule: base fee unless the (discounted) product amount reaches the free-shipping amount,
 * plus a Jeju / remote-island surcharge that applies regardless of free shipping.
 */
public final class ShippingFeeCalculator {

    private ShippingFeeCalculator() {
    }

    public record Policy(
            BigDecimal baseShippingFee,
            BigDecimal freeShippingAmount,
            BigDecimal jejuExtraFee,
            BigDecimal remoteAreaExtraFee
    ) {
    }

    public static ShippingQuote quote(Policy policy, BigDecimal productAmount, ExtraAreaType areaType) {
        if (productAmount == null || productAmount.signum() <= 0) {
            return new ShippingQuote(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    false, areaType, policy.freeShippingAmount());
        }
        boolean free = productAmount.compareTo(policy.freeShippingAmount()) >= 0;
        BigDecimal base = free ? BigDecimal.ZERO : policy.baseShippingFee();
        BigDecimal extra = areaType == ExtraAreaType.JEJU
                ? policy.jejuExtraFee()
                : areaType == ExtraAreaType.REMOTE ? policy.remoteAreaExtraFee() : BigDecimal.ZERO;
        return new ShippingQuote(productAmount, base, extra, base.add(extra), free, areaType,
                policy.freeShippingAmount());
    }
}
