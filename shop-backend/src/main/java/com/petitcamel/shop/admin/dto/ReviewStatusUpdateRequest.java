package com.petitcamel.shop.admin.dto;

import com.petitcamel.shop.review.domain.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewStatusUpdateRequest(
        @NotNull ReviewStatus status
) {
}
