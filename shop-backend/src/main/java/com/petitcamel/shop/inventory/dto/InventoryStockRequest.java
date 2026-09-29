package com.petitcamel.shop.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryStockRequest(
        @NotNull @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "재고 수량이 너무 큽니다.")
        Integer stockQuantity,

        @Size(max = 255) String reason
) {
}
