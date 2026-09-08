package com.petitcamel.shop.recommendation.provider;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RuleBasedOutfitRecommendationProvider implements OutfitRecommendationProvider {

    public static final String PROVIDER_NAME = "rule";

    @Override
    public OutfitRecommendationResult recommend(OutfitRecommendationContext context) {
        List<OutfitCandidate> filtered = filterCandidates(context);
        List<OutfitRecommendationResult.Outfit> outfits = buildOutfits(filtered, context);
        return new OutfitRecommendationResult(PROVIDER_NAME, null, outfits);
    }

    List<OutfitCandidate> filterCandidates(OutfitRecommendationContext context) {
        List<String> requestedColors = normalizeColors(context.colors());
        String occasion = normalize(context.occasion());
        String style = normalize(context.style());

        return context.candidates().stream()
                .filter(c -> c.availableQuantity() > 0)
                .filter(c -> withinBudget(c.salePrice(), context.budget()))
                .filter(c -> matchesColors(c.colors(), requestedColors))
                .filter(c -> matchesOccasionStyle(c, occasion, style))
                .sorted(Comparator
                        .comparing(OutfitCandidate::salePrice, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OutfitCandidate::productId))
                .toList();
    }

    private List<OutfitRecommendationResult.Outfit> buildOutfits(
            List<OutfitCandidate> filtered,
            OutfitRecommendationContext context) {
        if (filtered.isEmpty()) {
            return List.of();
        }

        List<OutfitCandidate> tops = byCategoryHint(filtered, "top", "shirt", "women-tops");
        List<OutfitCandidate> bottoms = byCategoryHint(filtered, "bottom", "pants", "women-bottoms");
        List<OutfitCandidate> dresses = byCategoryHint(filtered, "dress", "women-dresses");
        List<OutfitCandidate> kids = byCategoryHint(filtered, "kid", "kids-girls");

        List<OutfitRecommendationResult.Outfit> outfits = new ArrayList<>();

        String occasion = normalize(context.occasion());
        if (occasion.contains("kid") || occasion.contains("child")) {
            for (OutfitCandidate kid : kids.stream().limit(3).toList()) {
                outfits.add(new OutfitRecommendationResult.Outfit(List.of(kid.productId())));
            }
            return outfits;
        }

        for (OutfitCandidate dress : dresses) {
            if (outfits.size() >= 3) {
                break;
            }
            outfits.add(new OutfitRecommendationResult.Outfit(List.of(dress.productId())));
        }

        int topIdx = 0;
        int bottomIdx = 0;
        while (outfits.size() < 3 && (!tops.isEmpty() || !bottoms.isEmpty())) {
            List<Long> items = new ArrayList<>();
            if (topIdx < tops.size()) {
                items.add(tops.get(topIdx++).productId());
            }
            if (bottomIdx < bottoms.size()) {
                items.add(bottoms.get(bottomIdx++).productId());
            }
            if (items.isEmpty()) {
                break;
            }
            Set<Long> unique = new LinkedHashSet<>(items);
            outfits.add(new OutfitRecommendationResult.Outfit(List.copyOf(unique)));
        }

        if (outfits.isEmpty()) {
            List<Long> fallback = filtered.stream()
                    .limit(3)
                    .map(OutfitCandidate::productId)
                    .toList();
            if (!fallback.isEmpty()) {
                outfits.add(new OutfitRecommendationResult.Outfit(fallback));
            }
        }

        return outfits.stream().limit(3).toList();
    }

    private List<OutfitCandidate> byCategoryHint(List<OutfitCandidate> candidates, String... hints) {
        return candidates.stream()
                .filter(c -> {
                    String slug = normalize(c.categorySlug());
                    String name = normalize(c.productName());
                    for (String hint : hints) {
                        if (slug.contains(hint) || name.contains(hint)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }

    private boolean matchesOccasionStyle(OutfitCandidate candidate, String occasion, String style) {
        String slug = normalize(candidate.categorySlug());
        String name = normalize(candidate.productName());

        if (occasion.contains("kid") || occasion.contains("child") || style.contains("kid")) {
            return slug.contains("kid") || name.contains("키즈") || name.contains("kid");
        }
        if (occasion.contains("date") || occasion.contains("party") || style.contains("elegant")) {
            return slug.contains("dress") || name.contains("원피스") || slug.contains("top") || slug.contains("bottom");
        }
        if (occasion.contains("office") || style.contains("formal")) {
            return slug.contains("top") || slug.contains("bottom") || slug.contains("dress");
        }
        // casual / daily / default — exclude kids-only unless requested
        return !slug.contains("kid");
    }

    private boolean matchesColors(List<String> productColors, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return true;
        }
        if (productColors == null || productColors.isEmpty()) {
            return false;
        }
        Set<String> available = productColors.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .collect(Collectors.toSet());
        return requested.stream().anyMatch(available::contains);
    }

    private boolean withinBudget(BigDecimal salePrice, BigDecimal budget) {
        if (budget == null) {
            return true;
        }
        if (salePrice == null) {
            return false;
        }
        return salePrice.compareTo(budget) <= 0;
    }

    private List<String> normalizeColors(List<String> colors) {
        if (colors == null) {
            return List.of();
        }
        return colors.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
