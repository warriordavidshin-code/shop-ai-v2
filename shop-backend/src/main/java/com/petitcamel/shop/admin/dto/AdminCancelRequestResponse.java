package com.petitcamel.shop.admin.dto;

import com.petitcamel.shop.order.domain.CancelRequestStatus;
import com.petitcamel.shop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminCancelRequestResponse(
        Long cancelRequestId,
        Long orderId,
        String orderNo,
        OrderStatus orderStatus,
        Long memberId,
        String memberLoginId,
        String memberName,
        CancelRequestStatus status,
        String reason,
        OrderStatus previousOrderStatus,
        String rejectReason,
        BigDecimal paymentAmount,
        String itemSummary,
        Instant requestedAt,
        Instant processedAt
) {
}
