package com.petitcamel.shop.shipping.provider;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Vendor-neutral tracking result. The same statuses apply to deliveries and returns.
 *
 * @param found  false when the vendor has no record for the invoice yet
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

    /**
     * @param externalEventId vendor event id when the vendor has one (used for de-duplication)
     * @param providerStatus  the vendor's own status wording or code
     * @param status          internal status it maps to, or null
     */
    public record Event(
            Instant time,
            String externalEventId,
            String providerStatus,
            ShipmentStatus status,
            String location,
            String description
    ) {
    }
}
