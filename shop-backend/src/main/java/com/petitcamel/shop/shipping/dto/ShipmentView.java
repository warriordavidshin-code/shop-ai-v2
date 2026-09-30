package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentType;

import java.time.Instant;
import java.util.List;

/**
 * @param deliveryCompany  courier code (HANJIN)
 * @param shippingProvider vendor code that last handled the parcel (SWEETTRACKER), or null when handled by hand
 */
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
        String shippingProvider,
        Instant pickupRequestedAt,
        Instant pickedUpAt,
        Instant shippedAt,
        Instant outForDeliveryAt,
        Instant deliveredAt,
        Instant lastTrackingCheckedAt,
        String lastTrackingError,
        Instant lastStatusChangedAt,
        List<TrackingEventResponse> events
) {
}
