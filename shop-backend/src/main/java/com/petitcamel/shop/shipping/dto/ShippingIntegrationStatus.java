package com.petitcamel.shop.shipping.dto;

/** What the admin settings page shows about the tracking integration (never the key itself). */
public record ShippingIntegrationStatus(
        String requestedProvider,
        String activeProvider,
        boolean externalTracking,
        boolean apiKeyConfigured,
        String pickupService,
        boolean waybillSupported,
        boolean schedulerEnabled,
        String schedulerCron,
        int cacheMinutes
) {
}
