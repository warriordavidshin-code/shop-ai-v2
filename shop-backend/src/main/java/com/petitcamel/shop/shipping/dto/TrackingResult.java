package com.petitcamel.shop.shipping.dto;

import java.time.Instant;
import java.util.List;

/**
 * Customer tracking response ({@code GET /api/orders/{orderId}/tracking}).
 *
 * @param status           shipment status, or null before the order has a shipment
 * @param externalTracking true when events come from a courier tracking API
 * @param message          notice such as a temporary provider outage; null when all is well
 */
public record TrackingResult(
        Long orderId,
        String orderNo,
        String orderStatus,
        String shipmentType,
        String deliveryCompany,
        String deliveryCompanyName,
        String trackingNumber,
        String trackingUrl,
        String status,
        String statusName,
        Instant shippedAt,
        Instant deliveredAt,
        Instant lastCheckedAt,
        boolean externalTracking,
        String message,
        List<TrackingEventResponse> events
) {
}
