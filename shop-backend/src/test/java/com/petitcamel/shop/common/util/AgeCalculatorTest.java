package com.petitcamel.shop.common.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AgeCalculatorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void computesFullYearsUsingFixedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), SEOUL);
        AgeCalculator calculator = new AgeCalculator(clock);

        assertThat(calculator.ageInYears(LocalDate.of(1990, 1, 1))).isEqualTo(36);
        assertThat(calculator.ageInYears(LocalDate.of(2012, 9, 5))).isEqualTo(13);
        assertThat(calculator.ageInYears(LocalDate.of(2012, 9, 4))).isEqualTo(14);
    }
}
