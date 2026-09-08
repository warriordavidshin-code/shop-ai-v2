package com.petitcamel.shop.order.dto;

import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String orderNo,
        OrderStatus orderStatus,
        BigDecimal totalProductAmount,
        BigDecimal discountAmount,
        BigDecimal deliveryAmount,
        BigDecimal paymentAmount,
        String receiverName,
        String receiverPhone,
        String postcode,
        String address1,
        String address2,
        String orderMemo,
        Instant orderedAt,
        List<OrderItemResponse> items,
        PaymentStatus paymentStatus,
        String paymentMethod
) {
}
