package com.petitcamel.shop.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull Long skuId,
        @NotNull @Min(1) Integer quantity
) {
}
