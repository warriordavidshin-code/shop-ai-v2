package com.petitcamel.shop.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryAdjustRequest(
        @NotNull Integer quantityDelta,
        @NotBlank @Size(max = 255) String reason,
        /* true records the change as DAMAGE (파손·폐기) instead of ADJUSTMENT */
        boolean damage
) {
    public InventoryAdjustRequest(Integer quantityDelta, String reason) {
        this(quantityDelta, reason, false);
    }
}
