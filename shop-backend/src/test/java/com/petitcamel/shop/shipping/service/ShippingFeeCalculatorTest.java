package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.shipping.domain.ExtraAreaType;
import com.petitcamel.shop.shipping.dto.ShippingQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingFeeCalculatorTest {

    private static final ShippingFeeCalculator.Policy POLICY = new ShippingFeeCalculator.Policy(
            new BigDecimal("3000"), new BigDecimal("50000"), new BigDecimal("3000"), new BigDecimal("5000"));

    @Test
    void belowThresholdPaysBaseFee() {
        ShippingQuote quote = ShippingFeeCalculator.quote(POLICY, new BigDecimal("49999"), null);
        assertThat(quote.deliveryFee()).isEqualByComparingTo("3000");
        assertThat(quote.freeShipping()).isFalse();
    }

    @Test
    void thresholdAmountIsFree() {
        ShippingQuote quote = ShippingFeeCalculator.quote(POLICY, new BigDecimal("50000"), null);
        assertThat(quote.deliveryFee()).isEqualByComparingTo("0");
        assertThat(quote.freeShipping()).isTrue();
    }

    @Test
    void emptyCartHasNoFee() {
        assertThat(ShippingFeeCalculator.quote(POLICY, BigDecimal.ZERO, null).deliveryFee()).isEqualByComparingTo("0");
        assertThat(ShippingFeeCalculator.quote(POLICY, null, null).deliveryFee()).isEqualByComparingTo("0");
    }

    @Test
    void jejuSurchargeAppliesEvenWhenFree() {
        ShippingQuote free = ShippingFeeCalculator.quote(POLICY, new BigDecimal("80000"), ExtraAreaType.JEJU);
        assertThat(free.baseFee()).isEqualByComparingTo("0");
        assertThat(free.extraFee()).isEqualByComparingTo("3000");
        assertThat(free.deliveryFee()).isEqualByComparingTo("3000");
        assertThat(free.freeShipping()).isTrue();

        ShippingQuote paid = ShippingFeeCalculator.quote(POLICY, new BigDecimal("10000"), ExtraAreaType.JEJU);
        assertThat(paid.deliveryFee()).isEqualByComparingTo("6000");
    }

    @Test
    void remoteAreaSurcharge() {
        ShippingQuote quote = ShippingFeeCalculator.quote(POLICY, new BigDecimal("10000"), ExtraAreaType.REMOTE);
        assertThat(quote.extraFee()).isEqualByComparingTo("5000");
        assertThat(quote.deliveryFee()).isEqualByComparingTo("8000");
    }

    @Test
    void customPolicyValuesAreUsed() {
        ShippingFeeCalculator.Policy custom = new ShippingFeeCalculator.Policy(
                new BigDecimal("2500"), new BigDecimal("30000"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(ShippingFeeCalculator.quote(custom, new BigDecimal("29000"), null).deliveryFee())
                .isEqualByComparingTo("2500");
        assertThat(ShippingFeeCalculator.quote(custom, new BigDecimal("30000"), null).deliveryFee())
                .isEqualByComparingTo("0");
    }

    @Test
    void normalizePostcodeKeepsOnlyFiveDigits() {
        assertThat(ShippingFeeService.normalizePostcode("63-001")).isEqualTo("63001");
        assertThat(ShippingFeeService.normalizePostcode(" 06236 ")).isEqualTo("06236");
        assertThat(ShippingFeeService.normalizePostcode("123")).isNull();
        assertThat(ShippingFeeService.normalizePostcode(null)).isNull();
    }
}
