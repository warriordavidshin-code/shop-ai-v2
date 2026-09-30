package com.petitcamel.shop.shipping.dto;

import java.time.Instant;

/** @param time Asia/Seoul "yyyy-MM-dd HH:mm" for display; {@code timestamp} is the exact instant */
public record TrackingEventResponse(
        String time,
        Instant timestamp,
        String location,
        String description,
        String status,
        String source
) {
}
