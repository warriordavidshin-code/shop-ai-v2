package com.petitcamel.shop.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        BigDecimal productAmount,
        BigDecimal deliveryAmount,
        BigDecimal paymentAmount
) {
}
