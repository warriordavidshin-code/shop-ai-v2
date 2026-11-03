package com.petitcamel.shop.pricing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceCalculatorTest {

    private static final PriceCalculator.Basis VAT_INCLUDED =
            PriceCalculator.Basis.of("VAT_INCLUDED", "GENERAL", new BigDecimal("0.10"));
    private static final PriceCalculator.Basis VAT_EXCLUDED =
            PriceCalculator.Basis.of("VAT_EXCLUDED", "GENERAL", new BigDecimal("0.10"));

    @ParameterizedTest
    @CsvSource({
            "37200, END_900_ROUND_9000, 37900",
            "42100, END_900_ROUND_9000, 42900",
            "48300, END_900_ROUND_9000, 49000",
            "37900, END_900_ROUND_9000, 37900",
            "48300, END_900, 48900",
            "37201, UP_100, 37300",
            "37201, UP_1000, 38000",
            "37201, NONE, 37201"
    })
    void roundsPriceEndings(String raw, String mode, long expected) {
        assertThat(PriceCalculator.round(new BigDecimal(raw), mode)).isEqualTo(expected);
    }

    @Test
    void targetPriceForFiftyPercentMarginDoublesCost() {
        BigDecimal price = PriceCalculator.targetPrice(new BigDecimal("20000"), new BigDecimal("0.50"), VAT_INCLUDED);
        assertThat(price).isEqualByComparingTo("40000");
        assertThat(PriceCalculator.marginRate(new BigDecimal("40000"), new BigDecimal("20000"), VAT_INCLUDED))
                .isEqualByComparingTo("0.5");
    }

    @Test
    void vatExcludedBasisMeasuresMarginOnNetPrice() {
        BigDecimal price = PriceCalculator.targetPrice(new BigDecimal("20000"), new BigDecimal("0.50"), VAT_EXCLUDED);
        assertThat(price).isEqualByComparingTo("44000");
        assertThat(PriceCalculator.marginRate(new BigDecimal("44000"), new BigDecimal("20000"), VAT_EXCLUDED))
                .isEqualByComparingTo("0.5");
        assertThat(PriceCalculator.grossProfit(new BigDecimal("44000"), new BigDecimal("20000"), VAT_EXCLUDED))
                .isEqualByComparingTo("20000");
    }

    @Test
    void simplifiedTaxpayerAlwaysUsesVatIncludedBasis() {
        assertThat(PriceCalculator.Basis.of("VAT_EXCLUDED", "SIMPLIFIED", new BigDecimal("0.10")).excludeVat())
                .isFalse();
    }

    @Test
    void marginAfterRoundingIsRecomputed() {
        BigDecimal cost = new BigDecimal("18600");
        long rounded = PriceCalculator.round(PriceCalculator.targetPrice(cost, new BigDecimal("0.50"), VAT_INCLUDED),
                "END_900_ROUND_9000");
        assertThat(rounded).isEqualTo(37900);
        assertThat(PriceCalculator.marginRate(BigDecimal.valueOf(rounded), cost, VAT_INCLUDED))
                .isGreaterThanOrEqualTo(new BigDecimal("0.50"));
    }

    @Test
    void rejectsMarginOfOneHundredPercent() {
        assertThatThrownBy(() -> PriceCalculator.targetPrice(BigDecimal.TEN, BigDecimal.ONE, VAT_INCLUDED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
