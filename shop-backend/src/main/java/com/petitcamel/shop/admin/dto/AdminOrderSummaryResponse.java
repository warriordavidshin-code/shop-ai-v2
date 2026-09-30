package com.petitcamel.shop.admin.dto;

import com.petitcamel.shop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin order list row, including delivery / return state.
 *
 * @param pickupStatus 수거요청 column: "수거요청" / "집하완료" or null when no pickup was requested
 */
public record AdminOrderSummaryResponse(
        Long orderId,
        String orderNo,
        Long memberId,
        String memberLoginId,
        String memberName,
        String receiverName,
        String itemSummary,
        OrderStatus orderStatus,
        BigDecimal paymentAmount,
        Instant orderedAt,
        int itemCount,
        String shipmentStatus,
        String shipmentStatusName,
        String deliveryCompany,
        String deliveryCompanyName,
        String trackingNumber,
        String trackingUrl,
        String pickupStatus,
        String returnStatus,
        String returnStatusName
) {
}
