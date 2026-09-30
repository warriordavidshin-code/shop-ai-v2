package com.petitcamel.shop.shipping.event;

/**
 * Customer notification channel for shipment updates. Add a Kakao 알림톡 / SMS implementation and mark
 * it {@code @Primary} (or replace {@link LoggingShipmentNotifier}) to start sending real messages.
 */
public interface ShipmentNotifier {

    void notify(ShipmentNotification notification);

    record ShipmentNotification(
            Long memberId,
            Long orderId,
            String orderNo,
            String message
    ) {
    }
}
