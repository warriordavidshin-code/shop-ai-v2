package com.petitcamel.shop.review.dto;

import com.petitcamel.shop.review.domain.FitRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank String content,
        Integer heightCm,
        Integer weightKg,
        String purchasedSize,
        FitRating fitRating
) {
}
