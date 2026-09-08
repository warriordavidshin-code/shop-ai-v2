package com.petitcamel.shop.order.dto;

import com.petitcamel.shop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long orderId,
        String orderNo,
        OrderStatus orderStatus,
        BigDecimal paymentAmount,
        Instant orderedAt,
        int itemCount
) {
}
