package com.petitcamel.shop.reco.support;

import java.time.Instant;
import java.util.Set;

/** Everything the recommendation scores need about one product, built once per catalog snapshot. */
public record ProductProfile(
        Long productId,
        String productName,
        Long categoryId,
        String categoryName,
        String group,
        String itemType,
        Set<String> colorFamilies,
        String colorTone,
        Set<String> styles,
        Set<String> designs,
        Set<String> seasons,
        String material,
        Set<String> nameTokens,
        Set<String> keywordTokens,
        double price,
        Instant createdAt,
        String status,
        int availableStock,
        String featureSource
) {
    public boolean onSale() {
        return "ON_SALE".equals(status);
    }
}
