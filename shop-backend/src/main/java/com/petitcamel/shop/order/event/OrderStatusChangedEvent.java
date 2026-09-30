package com.petitcamel.shop.order.event;

import com.petitcamel.shop.order.domain.OrderStatus;

/** Published inside the order transaction when an admin or cancel flow changes the order status. */
public record OrderStatusChangedEvent(
        Long orderId,
        String orderNo,
        OrderStatus from,
        OrderStatus to,
        Long actorMemberId
) {
}
