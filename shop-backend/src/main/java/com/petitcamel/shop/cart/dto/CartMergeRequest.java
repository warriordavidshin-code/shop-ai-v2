package com.petitcamel.shop.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CartMergeRequest(
        @NotNull @Valid List<CartItemRequest> items
) {
}
