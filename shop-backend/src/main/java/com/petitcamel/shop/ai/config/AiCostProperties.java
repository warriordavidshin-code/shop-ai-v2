package com.petitcamel.shop.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * {@code app.ai.cost.*}: provider routing and spending limits. These are environment defaults; an admin can
 * override the switches and limits at runtime ({@code ai_settings}), but API keys are only ever read from the
 * environment and are never stored in the database or returned to the browser.
 */
@ConfigurationProperties(prefix = "app.ai.cost")
public record AiCostProperties(
        @DefaultValue("FREE_FIRST") String mode,
        @DefaultValue("true") boolean localOcrEnabled,
        @DefaultValue("false") boolean openaiEnabled,
        @DefaultValue("20") int dailyRequestLimit,
        @DefaultValue("1.0") BigDecimal monthlyBudgetUsd,
        @DefaultValue("true") boolean cacheEnabled,
        @DefaultValue("false") boolean paidFallbackEnabled,
        @DefaultValue Gemini gemini,
        @DefaultValue Ocr ocr
) {

    public record Gemini(
            @DefaultValue("") String apiKey,
            @DefaultValue("true") boolean enabled,
            @DefaultValue("gemini-2.5-flash") String model,
            @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
            /* false when the Google project has billing enabled: calls then count against the paid budget */
            @DefaultValue("true") boolean freeTier,
            @DefaultValue("0.30") BigDecimal inputPricePerMillion,
            @DefaultValue("2.50") BigDecimal outputPricePerMillion,
            @DefaultValue("10") int connectTimeoutSeconds,
            @DefaultValue("90") int timeoutSeconds,
            @DefaultValue("1") int maxRetries,
            @DefaultValue("1500") long retryBackoffMs,
            @DefaultValue("4000") int maxOutputTokens
    ) {
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Ocr(
            @DefaultValue("http://localhost:8866") String url,
            @DefaultValue("5") int connectTimeoutSeconds,
            @DefaultValue("120") int timeoutSeconds,
            /* parsed receipts below this confidence are escalated to Gemini when it is available */
            @DefaultValue("0.75") double minConfidence
    ) {
    }
}
