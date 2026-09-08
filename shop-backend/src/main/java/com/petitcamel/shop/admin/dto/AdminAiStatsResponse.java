package com.petitcamel.shop.admin.dto;

import java.util.Map;

public record AdminAiStatsResponse(
        long totalRecommendations,
        Map<String, Long> recommendationsByProvider,
        Map<String, Long> recommendationsByType,
        long totalEvents,
        Map<String, Long> eventsByType
) {
}
