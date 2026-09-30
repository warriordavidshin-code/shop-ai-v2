package com.petitcamel.shop.shipping.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingShipmentNotifier implements ShipmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingShipmentNotifier.class);

    @Override
    public void notify(ShipmentNotification notification) {
        log.info("[SHIPPING-NOTIFY] orderNo={} memberId={} message={}",
                notification.orderNo(), notification.memberId(), notification.message());
    }
}
