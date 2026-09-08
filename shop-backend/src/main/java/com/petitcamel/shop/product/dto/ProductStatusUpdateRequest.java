package com.petitcamel.shop.product.dto;

import com.petitcamel.shop.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusUpdateRequest(
        @NotNull ProductStatus status
) {
}
