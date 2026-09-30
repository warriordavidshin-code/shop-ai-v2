package com.petitcamel.shop.shipping.pickup;

import com.petitcamel.shop.shipping.domain.ShipmentType;

/** Pickup address is the shop for deliveries and the customer for returns. */
public record PickupRequest(
        Long orderId,
        String orderNo,
        ShipmentType shipmentType,
        String deliveryCompany,
        String contactName,
        String contactPhone,
        String postcode,
        String address1,
        String address2,
        String memo
) {
}
