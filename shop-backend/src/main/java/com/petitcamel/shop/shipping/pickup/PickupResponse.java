package com.petitcamel.shop.shipping.pickup;

/**
 * @param automatic      true when the provider booked the pickup itself
 * @param trackingNumber invoice issued by the provider, if any
 */
public record PickupResponse(
        boolean accepted,
        boolean automatic,
        String trackingNumber,
        String message
) {
}
