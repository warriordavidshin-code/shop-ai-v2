package com.petitcamel.shop.banner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HeroBannerRequest(
        @NotBlank @Size(max = 500) String imageUrl,
        @Size(max = 500) String overlayText,
        @NotNull Integer sortOrder,
        @NotNull Boolean active
) {
}
