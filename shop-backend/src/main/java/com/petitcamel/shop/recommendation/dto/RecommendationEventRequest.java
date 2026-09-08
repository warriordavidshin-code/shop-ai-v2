package com.petitcamel.shop.recommendation.dto;

import com.petitcamel.shop.recommendation.domain.RecommendationEventType;
import jakarta.validation.constraints.NotNull;

public record RecommendationEventRequest(
        @NotNull RecommendationEventType eventType,
        Long productId
) {
}
