package com.petitcamel.shop.shipping.dto;

/**
 * What the admin settings page shows about the integration (never the key itself).
 *
 * @param requestedProvider  SHIPPING_PROVIDER value
 * @param activeProvider     vendor serving tracking, or MANUAL when none qualifies
 * @param pickupService      vendor serving delivery pickups (MANUAL when booked by hand)
 * @param waybillProvider    vendor issuing waybills, or null when unavailable
 * @param returnPickupProvider vendor serving return pickups
 */
public record ShippingIntegrationStatus(
        String requestedProvider,
        String activeProvider,
        boolean externalTracking,
        boolean apiKeyConfigured,
        String pickupService,
        boolean waybillSupported,
        String waybillProvider,
        String returnPickupProvider,
        boolean schedulerEnabled,
        String schedulerCron,
        int cacheMinutes
) {
}
