package com.petitcamel.shop.shipping.event;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends customer notifications after the status change is committed; failures never affect shipping. */
@Component
public class ShipmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentNotificationListener.class);

    private final ShipmentNotifier notifier;

    public ShipmentNotificationListener(ShipmentNotifier notifier) {
        this.notifier = notifier;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(ShipmentStatusChangedEvent event) {
        String message = messageFor(event);
        if (message == null) {
            return;
        }
        try {
            notifier.notify(new ShipmentNotifier.ShipmentNotification(
                    event.memberId(), event.orderId(), event.orderNo(), message));
        } catch (RuntimeException ex) {
            log.warn("[SHIPPING-NOTIFY] failed orderNo={} error={}", event.orderNo(), ex.getClass().getSimpleName());
        }
    }

    static String messageFor(ShipmentStatusChangedEvent event) {
        if (event.isDispatch()) {
            return "상품이 발송되었습니다.";
        }
        ShipmentStatus to = event.to();
        return switch (to) {
            case OUT_FOR_DELIVERY -> "상품이 배송 출발했습니다. 곧 도착합니다.";
            case DELIVERED -> "상품 배송이 완료되었습니다.";
            case RETURN_PICKUP_REQUESTED -> "반품 수거가 접수되었습니다. 기사님이 방문할 예정입니다.";
            case RETURN_COMPLETED -> "반품 상품이 입고되었습니다. 상품 확인 후 환불이 진행됩니다.";
            default -> null;
        };
    }
}
