package com.petitcamel.shop.ai.domain;

public enum AiAnalysisType {
    RECEIPT,
    PRODUCT,
    PURCHASE_RECOMMENDATION,
    /** Re-analysis of an existing product's images for recommendation traits (admin request only). */
    PRODUCT_FEATURE
}
