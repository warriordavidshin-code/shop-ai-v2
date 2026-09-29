package com.petitcamel.shop.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryFeePolicyTest {

    @Test
    void chargesFeeBelowThreshold() {
        assertThat(DeliveryFeePolicy.feeFor(new BigDecimal("49999"))).isEqualByComparingTo("3000");
        assertThat(DeliveryFeePolicy.feeFor(new BigDecimal("49000.00"))).isEqualByComparingTo("3000");
    }

    @Test
    void freeShippingFromThreshold() {
        assertThat(DeliveryFeePolicy.feeFor(new BigDecimal("50000"))).isEqualByComparingTo("0");
        assertThat(DeliveryFeePolicy.feeFor(new BigDecimal("98000.00"))).isEqualByComparingTo("0");
    }

    @Test
    void noFeeForEmptyAmount() {
        assertThat(DeliveryFeePolicy.feeFor(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(DeliveryFeePolicy.feeFor(null)).isEqualByComparingTo("0");
    }
}
