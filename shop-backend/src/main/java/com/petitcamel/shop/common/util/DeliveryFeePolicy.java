package com.petitcamel.shop.common.util;

import java.math.BigDecimal;

/**
 * Shipping is free once the product amount (after discounts) reaches {@link #FREE_SHIPPING_THRESHOLD}.
 * The frontend mirrors these numbers in {@code features/orders/delivery.ts}.
 */
public final class DeliveryFeePolicy {

    public static final BigDecimal DELIVERY_FEE = BigDecimal.valueOf(3000);
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(50000);

    private DeliveryFeePolicy() {
    }

    public static BigDecimal feeFor(BigDecimal productAmount) {
        if (productAmount == null || productAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return productAmount.compareTo(FREE_SHIPPING_THRESHOLD) >= 0 ? BigDecimal.ZERO : DELIVERY_FEE;
    }
}
