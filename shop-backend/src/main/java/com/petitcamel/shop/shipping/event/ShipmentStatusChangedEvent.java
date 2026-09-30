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

    /** First transition into a picked-up/in-transit state, i.e. the parcel left the shop. */
    public boolean isDispatch() {
        return shipmentType == ShipmentType.DELIVERY
                && (from == null || from.getRank() < ShipmentStatus.PICKED_UP.getRank())
                && to.getRank() >= ShipmentStatus.PICKED_UP.getRank()
                && to != ShipmentStatus.DELIVERED
                && to.belongsTo(ShipmentType.DELIVERY);
    }
}
