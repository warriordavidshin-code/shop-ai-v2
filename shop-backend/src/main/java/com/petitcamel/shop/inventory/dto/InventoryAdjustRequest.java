package com.petitcamel.shop.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryAdjustRequest(
        @NotNull Integer quantityDelta,
        @NotBlank @Size(max = 255) String reason
) {
}
