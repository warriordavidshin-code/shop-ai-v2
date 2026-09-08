package com.petitcamel.shop.admin.service;

import com.petitcamel.shop.admin.dto.AdminAiStatsResponse;
import com.petitcamel.shop.recommendation.repository.AiRecommendationRepository;
import com.petitcamel.shop.recommendation.repository.RecommendationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAiService {

    private final AiRecommendationRepository aiRecommendationRepository;
    private final RecommendationEventRepository recommendationEventRepository;

    public AdminAiService(
            AiRecommendationRepository aiRecommendationRepository,
            RecommendationEventRepository recommendationEventRepository) {
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.recommendationEventRepository = recommendationEventRepository;
    }

    @Transactional(readOnly = true)
    public AdminAiStatsResponse getStats() {
        Map<String, Long> byProvider = toCountMap(aiRecommendationRepository.countGroupedByProvider());
        Map<String, Long> byType = toCountMap(aiRecommendationRepository.countGroupedByType());
        Map<String, Long> eventsByType = toCountMap(recommendationEventRepository.countGroupedByEventType());

        long totalRecommendations = byProvider.values().stream().mapToLong(Long::longValue).sum();
        long totalEvents = eventsByType.values().stream().mapToLong(Long::longValue).sum();

        return new AdminAiStatsResponse(
                totalRecommendations,
                byProvider,
                byType,
                totalEvents,
                eventsByType);
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Object key = row[0];
            Number count = (Number) row[1];
            result.put(key == null ? "UNKNOWN" : key.toString(), count.longValue());
        }
        return result;
    }
}
