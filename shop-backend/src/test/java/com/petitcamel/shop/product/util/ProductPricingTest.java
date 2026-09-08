package com.petitcamel.shop.product.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductPricingTest {

    @Test
    void discountRateRoundsHalfUp() {
        assertThat(ProductPricing.discountRate(new BigDecimal("59000"), new BigDecimal("49000")))
                .isEqualTo(17);
        assertThat(ProductPricing.discountRate(new BigDecimal("100"), new BigDecimal("50")))
                .isEqualTo(50);
        assertThat(ProductPricing.discountRate(new BigDecimal("3"), new BigDecimal("1")))
                .isEqualTo(67);
    }

    @Test
    void discountRateIsZeroWhenNormalNotPositive() {
        assertThat(ProductPricing.discountRate(BigDecimal.ZERO, new BigDecimal("10"))).isEqualTo(0);
        assertThat(ProductPricing.discountRate(new BigDecimal("-1"), new BigDecimal("10"))).isEqualTo(0);
        assertThat(ProductPricing.discountRate(null, new BigDecimal("10"))).isEqualTo(0);
    }

    @Test
    void availableQuantityNeverNegative() {
        assertThat(ProductPricing.availableQuantity(10, 3)).isEqualTo(7);
        assertThat(ProductPricing.availableQuantity(0, 0)).isEqualTo(0);
        assertThat(ProductPricing.availableQuantity(2, 5)).isEqualTo(0);
    }
}
