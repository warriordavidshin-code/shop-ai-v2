package com.petitcamel.shop.shipping.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the courier for in-transit shipments. Delivered / cancelled shipments are never polled. */
@Component
@ConditionalOnProperty(prefix = "shipping.tracking", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ShipmentTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingScheduler.class);

    private final TrackingService trackingService;

    public ShipmentTrackingScheduler(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @Scheduled(cron = "${shipping.tracking.cron:0 */30 * * * *}", zone = "Asia/Seoul")
    public void refreshInTransitShipments() {
        try {
            trackingService.refreshDueShipments();
        } catch (RuntimeException ex) {
            log.warn("[SHIPPING] scheduled tracking failed error={}", ex.getClass().getSimpleName());
        }
    }
}
