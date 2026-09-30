package com.petitcamel.shop.shipping.event;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentType;

import java.time.Instant;

public record ShipmentStatusChangedEvent(
        Long shipmentId,
        Long orderId,
        String orderNo,
        Long memberId,
        ShipmentType shipmentType,
        ShipmentStatus from,
        ShipmentStatus to,
        String deliveryCompany,
        String trackingNumber,
        Instant changedAt
) {

    public static final String DELIVERY_DISPATCHED = "DELIVERY_DISPATCHED";
    public static final String DELIVERY_OUT_FOR_DELIVERY = "DELIVERY_OUT_FOR_DELIVERY";
    public static final String DELIVERY_DELIVERED = "DELIVERY_DELIVERED";
    public static final String RETURN_PICKUP_REQUESTED = "RETURN_PICKUP_REQUESTED";
    public static final String RETURN_RECEIVED = "RETURN_RECEIVED";

    /** First transition into a moving state of a delivery, i.e. the parcel left the shop. */
    public boolean isDispatch() {
        return shipmentType == ShipmentType.DELIVERY
                && (from == null || !from.isMoving())
                && to.isMoving()
                && to != ShipmentStatus.DELIVERED;
    }

    /** Customer-facing notification this change triggers, or null. */
    public String notificationType() {
        if (shipmentType == ShipmentType.DELIVERY) {
            if (isDispatch()) {
                return DELIVERY_DISPATCHED;
            }
            return switch (to) {
                case OUT_FOR_DELIVERY -> DELIVERY_OUT_FOR_DELIVERY;
                case DELIVERED -> DELIVERY_DELIVERED;
                default -> null;
            };
        }
        if (shipmentType == ShipmentType.RETURN) {
            return switch (to) {
                case PICKUP_REQUESTED -> RETURN_PICKUP_REQUESTED;
                case DELIVERED -> RETURN_RECEIVED;
                default -> null;
            };
        }
        return null;
    }
}
