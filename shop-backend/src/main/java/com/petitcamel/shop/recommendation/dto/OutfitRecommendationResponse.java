package com.petitcamel.shop.recommendation.dto;

import com.petitcamel.shop.product.dto.ProductSummaryResponse;

import java.util.List;

public record OutfitRecommendationResponse(
        Long recommendationId,
        String provider,
        List<Outfit> outfits
) {
    public record Outfit(List<ProductSummaryResponse> items) {
    }
}
