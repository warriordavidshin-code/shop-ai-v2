package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentType;

import java.time.Instant;
import java.util.List;

public record ShipmentView(
        Long shipmentId,
        Long orderId,
        ShipmentType shipmentType,
        ShipmentStatus status,
        String statusName,
        String deliveryCompany,
        String deliveryCompanyName,
        String trackingNumber,
        String trackingUrl,
        Instant pickupRequestedAt,
        Instant pickedUpAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant lastTrackingCheckedAt,
        String lastTrackingError,
        List<TrackingEventResponse> events
) {
}
