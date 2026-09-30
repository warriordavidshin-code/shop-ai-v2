package com.petitcamel.shop.shipping.dto;

public record ShipmentActionResponse(
        boolean success,
        String message,
        ShipmentView shipment
) {
}
