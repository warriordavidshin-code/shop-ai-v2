package com.petitcamel.shop.payment.dto;

import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long paymentId,
        String orderNo,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        BigDecimal paymentAmount,
        String paymentMethod,
        String providerTransactionId,
        Instant approvedAt
) {
}
