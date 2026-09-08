package com.petitcamel.shop.recommendation.dto;

import jakarta.validation.constraints.NotNull;

public record SizeRecommendationRequest(
        @NotNull Long productId,
        Integer heightCm,
        Integer weightKg,
        String preferredFit
) {
}
