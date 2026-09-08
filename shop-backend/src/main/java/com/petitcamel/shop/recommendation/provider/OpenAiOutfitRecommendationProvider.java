package com.petitcamel.shop.recommendation.provider;

import com.petitcamel.shop.recommendation.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selected only when {@code app.ai.provider=openai} and {@code OPENAI_API_KEY} is present.
 * Does not invent product IDs — only rearranges backend-provided candidates.
 * Makes no external OpenAI HTTP calls (inactive/no-op when key empty).
 */
@Component
public class OpenAiOutfitRecommendationProvider implements OutfitRecommendationProvider {

    public static final String PROVIDER_NAME = "openai";

    private static final Logger log = LoggerFactory.getLogger(OpenAiOutfitRecommendationProvider.class);

    private final AiProperties aiProperties;
    private final RuleBasedOutfitRecommendationProvider ruleBased;

    public OpenAiOutfitRecommendationProvider(
            AiProperties aiProperties,
            RuleBasedOutfitRecommendationProvider ruleBased) {
        this.aiProperties = aiProperties;
        this.ruleBased = ruleBased;
    }

    public boolean isActive() {
        return aiProperties.hasOpenAiKey();
    }

    @Override
    public OutfitRecommendationResult recommend(OutfitRecommendationContext context) {
        if (!isActive()) {
            log.debug("OpenAI provider inactive: OPENAI_API_KEY empty — no-op");
            return new OutfitRecommendationResult(PROVIDER_NAME, null, List.of());
        }

        Set<Long> allowedIds = context.candidates().stream()
                .map(OutfitCandidate::productId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // No external API call: score candidates locally, then constrain to allowed IDs only.
        OutfitRecommendationResult base = ruleBased.recommend(context);
        String model = blankToNull(aiProperties.getOpenai().getModel());

        List<OutfitRecommendationResult.Outfit> constrained = new ArrayList<>();
        for (OutfitRecommendationResult.Outfit outfit : base.outfits()) {
            List<Long> ids = outfit.productIds().stream()
                    .filter(allowedIds::contains)
                    .distinct()
                    .toList();
            if (!ids.isEmpty()) {
                constrained.add(new OutfitRecommendationResult.Outfit(ids));
            }
        }

        if (constrained.isEmpty() && !allowedIds.isEmpty()) {
            Map<Long, OutfitCandidate> byId = context.candidates().stream()
                    .filter(c -> c.availableQuantity() > 0)
                    .collect(Collectors.toMap(OutfitCandidate::productId, Function.identity(), (a, b) -> a));
            List<Long> fallback = allowedIds.stream()
                    .filter(byId::containsKey)
                    .limit(3)
                    .toList();
            if (!fallback.isEmpty()) {
                constrained.add(new OutfitRecommendationResult.Outfit(fallback));
            }
        }

        return new OutfitRecommendationResult(PROVIDER_NAME, model, constrained.stream().limit(3).toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
