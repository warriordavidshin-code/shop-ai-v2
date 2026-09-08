package com.petitcamel.shop.recommendation.dto;

import java.util.List;

public record SizeRecommendationResponse(
        Long productId,
        String recommendedSize,
        Confidence confidence,
        List<String> reasons
) {
    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW,
        UNCERTAIN
    }
}
