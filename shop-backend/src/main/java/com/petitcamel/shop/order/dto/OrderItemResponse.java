package com.petitcamel.shop.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long orderItemId,
        Long productId,
        Long skuId,
        String productName,
        String optionName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal discountPrice,
        BigDecimal paymentPrice,
        String status
) {
}
