package com.petitcamel.shop.recommendation.provider;

import java.math.BigDecimal;
import java.util.List;

public record OutfitCandidate(
        Long productId,
        Long categoryId,
        String productName,
        String categorySlug,
        BigDecimal salePrice,
        List<String> colors,
        int availableQuantity
) {
}
