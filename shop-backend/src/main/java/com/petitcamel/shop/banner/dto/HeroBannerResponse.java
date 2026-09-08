package com.petitcamel.shop.banner.dto;

import java.time.Instant;

public record HeroBannerResponse(
        Long bannerId,
        String imageUrl,
        String overlayText,
        Integer sortOrder,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
