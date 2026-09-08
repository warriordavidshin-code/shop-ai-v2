package com.petitcamel.shop.recommendation.config;

import com.petitcamel.shop.recommendation.provider.OpenAiOutfitRecommendationProvider;
import com.petitcamel.shop.recommendation.provider.OutfitRecommendationProvider;
import com.petitcamel.shop.recommendation.provider.RuleBasedOutfitRecommendationProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    @Primary
    public OutfitRecommendationProvider outfitRecommendationProvider(
            AiProperties aiProperties,
            RuleBasedOutfitRecommendationProvider ruleBased,
            OpenAiOutfitRecommendationProvider openAi) {
        if (aiProperties.isOpenAiSelected()) {
            return openAi;
        }
        return ruleBased;
    }
}
