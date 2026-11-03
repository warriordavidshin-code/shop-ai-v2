package com.petitcamel.shop.ai.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Asks the model for one JSON object matching {@code schema} (strict structured output).
 *
 * @param schemaName  identifier sent as the json_schema name ([a-zA-Z0-9_-])
 * @param instructions system-level rules
 * @param userText    task text placed before the files
 */
public record AiStructuredRequest(
        String schemaName,
        JsonNode schema,
        String instructions,
        String userText,
        List<AiInputFile> files
) {
}
