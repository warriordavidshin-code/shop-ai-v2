package com.petitcamel.shop.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public record OutfitRecommendationRequest(
        @NotBlank String occasion,
        @NotBlank String style,
        List<String> colors,
        BigDecimal budget
) {
}
