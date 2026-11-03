package com.petitcamel.shop.ai.client;

import com.fasterxml.jackson.databind.JsonNode;

public record AiStructuredResult(
        JsonNode output,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        int attempts
) {
}
