package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.order.event.OrderStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the delivery shipment in step when an admin changes the order status directly
 * (order status dropdown, cancellation). Runs inside the publisher's transaction.
 */
@Component
public class ShipmentOrderSyncListener {

    private final ShipmentService shipmentService;

    public ShipmentOrderSyncListener(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        shipmentService.syncFromOrderStatus(event.orderId(), event.to(), event.actorMemberId());
    }
}
