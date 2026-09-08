package com.petitcamel.shop.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long cartItemId,
        Long skuId,
        Long productId,
        String productName,
        String optionName,
        BigDecimal unitPrice,
        int quantity,
        int availableQuantity,
        BigDecimal lineTotal
) {
}
