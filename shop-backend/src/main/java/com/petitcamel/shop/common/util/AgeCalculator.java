package com.petitcamel.shop.common.util;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

@Component
public class AgeCalculator {

    private final Clock clock;

    public AgeCalculator(Clock clock) {
        this.clock = clock;
    }

    public int ageInYears(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("birthDate must not be null");
        }
        return Period.between(birthDate, LocalDate.now(clock)).getYears();
    }
}
