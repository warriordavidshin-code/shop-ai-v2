package com.petitcamel.shop.recommendation.provider;

import java.util.List;

public record OutfitRecommendationResult(
        String provider,
        String modelName,
        List<Outfit> outfits
) {
    public record Outfit(List<Long> productIds) {
    }
}
