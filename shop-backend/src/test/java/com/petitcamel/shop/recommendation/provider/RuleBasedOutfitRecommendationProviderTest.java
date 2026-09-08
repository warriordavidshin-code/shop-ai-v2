package com.petitcamel.shop.recommendation.provider;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedOutfitRecommendationProviderTest {

    private final RuleBasedOutfitRecommendationProvider provider = new RuleBasedOutfitRecommendationProvider();

    @Test
    void filtersOutZeroStockAndOverBudget() {
        List<OutfitCandidate> candidates = List.of(
                candidate(1L, "women-tops", "Ivory", 49000, 10),
                candidate(2L, "women-bottoms", "Beige", 59000, 0),
                candidate(3L, "women-dresses", "Ivory", 79000, 5),
                candidate(4L, "kids-girls", "Cream", 25000, 8));

        OutfitRecommendationContext context = new OutfitRecommendationContext(
                "daily",
                "casual",
                List.of("Ivory", "Beige"),
                new BigDecimal("60000"),
                candidates);

        List<OutfitCandidate> filtered = provider.filterCandidates(context);

        assertThat(filtered).extracting(OutfitCandidate::productId).containsExactly(1L);
        assertThat(filtered).noneMatch(c -> c.availableQuantity() <= 0);
        assertThat(filtered).noneMatch(c -> c.salePrice().compareTo(new BigDecimal("60000")) > 0);
    }

    @Test
    void buildsOutfitsOnlyFromCandidateProductIds() {
        List<OutfitCandidate> candidates = List.of(
                candidate(1L, "women-tops", "Ivory", 49000, 10),
                candidate(3L, "women-bottoms", "Beige", 59000, 8),
                candidate(2L, "women-dresses", "Ivory", 79000, 5));

        OutfitRecommendationResult result = provider.recommend(new OutfitRecommendationContext(
                "date",
                "elegant",
                List.of(),
                null,
                candidates));

        assertThat(result.provider()).isEqualTo("rule");
        assertThat(result.outfits()).isNotEmpty();
        assertThat(result.outfits()).hasSizeLessThanOrEqualTo(3);

        for (OutfitRecommendationResult.Outfit outfit : result.outfits()) {
            assertThat(outfit.productIds()).isNotEmpty();
            assertThat(List.of(1L, 2L, 3L)).containsAll(outfit.productIds());
        }
    }

    @Test
    void returnsEmptyWhenNoMatchingStock() {
        OutfitRecommendationResult result = provider.recommend(new OutfitRecommendationContext(
                "daily",
                "casual",
                List.of("Red"),
                null,
                List.of(candidate(1L, "women-tops", "Ivory", 49000, 0))));

        assertThat(result.outfits()).isEmpty();
    }

    private static OutfitCandidate candidate(
            long productId, String slug, String color, int price, int available) {
        return new OutfitCandidate(
                productId,
                productId,
                "Product-" + productId,
                slug,
                BigDecimal.valueOf(price),
                List.of(color),
                available);
    }
}
