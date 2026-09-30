package com.petitcamel.shop.shipping.provider;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Provider-neutral tracking result. {@code status} and event statuses use the delivery-direction
 * values (PICKED_UP ... DELIVERED); the shipping service converts them for return shipments.
 *
 * @param found  false when the provider has no record for the invoice yet
 * @param status latest mapped status, or null when it could not be mapped
 */
public record TrackingResponse(
        boolean found,
        ShipmentStatus status,
        List<Event> events,
        String message
) {

    public static TrackingResponse notFound(String message) {
        return new TrackingResponse(false, null, List.of(), message);
    }

    public record Event(
            Instant time,
            String location,
            String description,
            ShipmentStatus status
    ) {
    }
}
