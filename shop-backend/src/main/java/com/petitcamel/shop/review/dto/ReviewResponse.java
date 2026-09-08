package com.petitcamel.shop.review.dto;

import com.petitcamel.shop.review.domain.FitRating;
import com.petitcamel.shop.review.domain.ReviewStatus;

import java.time.Instant;

public record ReviewResponse(
        Long reviewId,
        Long productId,
        Long orderItemId,
        Integer rating,
        String content,
        Integer heightCm,
        Integer weightKg,
        String purchasedSize,
        FitRating fitRating,
        ReviewStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
