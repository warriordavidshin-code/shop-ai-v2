package com.petitcamel.shop.recommendation.provider;

import java.math.BigDecimal;
import java.util.List;

public record OutfitRecommendationContext(
        String occasion,
        String style,
        List<String> colors,
        BigDecimal budget,
        List<OutfitCandidate> candidates
) {
}
