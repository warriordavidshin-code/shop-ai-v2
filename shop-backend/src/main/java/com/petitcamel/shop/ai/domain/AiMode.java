package com.petitcamel.shop.ai.domain;

/** Provider routing mode chosen by the admin (AI_MODE / ai_settings.ai_mode). */
public enum AiMode {
    /** Local OCR + Java rules, then Gemini free tier, then OpenAI only when enabled and approved, then manual. */
    FREE_FIRST,
    /** Gemini directly (no local OCR step); OpenAI only when enabled and approved. */
    GEMINI,
    /** OpenAI first (explicit admin choice; still subject to the monthly budget), Gemini as fallback. */
    OPENAI,
    /** Local OCR + rules only. No external API is ever called. */
    LOCAL_ONLY,
    /** AI switched off; all data is entered manually. */
    MANUAL;

    public static AiMode parse(String value, AiMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return AiMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
