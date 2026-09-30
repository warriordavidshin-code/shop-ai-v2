package com.petitcamel.shop.shipping.provider;

/**
 * Result of a state-changing vendor call.
 *
 * @param automatic         true when the vendor did the work itself (false for MANUAL, where an admin books it)
 * @param trackingNumber    invoice issued by the vendor, if any
 * @param externalReference vendor's id for the booking / waybill, if any
 * @param printUrl          waybill label URL, if any
 */
public record ProviderActionResult(
        boolean automatic,
        String trackingNumber,
        String externalReference,
        String printUrl,
        String message
) {
}
