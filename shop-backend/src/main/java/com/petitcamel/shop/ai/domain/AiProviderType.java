package com.petitcamel.shop.ai.domain;

public enum AiProviderType {
    OPENAI,
    /** Google Gemini API (free tier first). */
    GEMINI,
    /** On-server PaddleOCR sidecar + Java rules; no external API, no cost. */
    LOCAL_OCR,
    /** Sample results for development / tests; never presented as a real analysis. */
    MOCK
}
