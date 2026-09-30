package com.petitcamel.shop.shipping.dto;

import java.time.Instant;

/**
 * @param time           Asia/Seoul "yyyy-MM-dd HH:mm" for display; {@code timestamp} is the exact instant
 * @param status         internal status, or null when the courier wording did not map to one
 * @param providerStatus the courier's own status wording
 */
public record TrackingEventResponse(
        String time,
        Instant timestamp,
        String location,
        String description,
        String status,
        String providerStatus,
        String source
) {
}
