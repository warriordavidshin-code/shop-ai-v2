package com.petitcamel.shop.admin.dto;

import com.petitcamel.shop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminOrderSummaryResponse(
        Long orderId,
        String orderNo,
        Long memberId,
        OrderStatus orderStatus,
        BigDecimal paymentAmount,
        Instant orderedAt,
        int itemCount
) {
}
