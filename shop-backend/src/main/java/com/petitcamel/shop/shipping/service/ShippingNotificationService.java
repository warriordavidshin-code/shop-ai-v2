package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.shipping.domain.NotificationStatus;
import com.petitcamel.shop.shipping.domain.ShippingNotification;
import com.petitcamel.shop.shipping.event.ShipmentNotifier;
import com.petitcamel.shop.shipping.event.ShipmentStatusChangedEvent;
import com.petitcamel.shop.shipping.repository.ShippingNotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Records each customer notification in {@code shipping_notification} before sending it. The unique
 * (shipment_id, event_type) key makes sure a repeated tracking sync never notifies the customer twice.
 */
@Service
public class ShippingNotificationService {

    static final String CHANNEL = "LOG";

    private final ShippingNotificationRepository repository;
    private final ShipmentNotifier notifier;
    private final TransactionTemplate newTx;
    private final Clock clock;

    public ShippingNotificationService(
            ShippingNotificationRepository repository,
            ShipmentNotifier notifier,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.repository = repository;
        this.notifier = notifier;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** @return false when this notification was already recorded */
    public boolean send(ShipmentStatusChangedEvent event, String eventType, String message) {
        ShippingNotification row;
        try {
            row = newTx.execute(tx -> {
                if (repository.existsByShipmentIdAndEventType(event.shipmentId(), eventType)) {
                    return null;
                }
                ShippingNotification created = new ShippingNotification();
                created.setOrderId(event.orderId());
                created.setShipmentId(event.shipmentId());
                created.setMemberId(event.memberId());
                created.setEventType(eventType);
                created.setChannel(CHANNEL);
                created.setStatus(NotificationStatus.PENDING);
                created.setMessage(message);
                return repository.saveAndFlush(created);
            });
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
        if (row == null) {
            return false;
        }
        NotificationStatus status;
        String error = null;
        if (event.memberId() == null) {
            status = NotificationStatus.SKIPPED;
        } else {
            try {
                notifier.notify(new ShipmentNotifier.ShipmentNotification(
                        event.memberId(), event.orderId(), event.orderNo(), message));
                status = NotificationStatus.SENT;
            } catch (RuntimeException ex) {
                status = NotificationStatus.FAILED;
                error = ex.getClass().getSimpleName();
            }
        }
        NotificationStatus finalStatus = status;
        String finalError = error;
        newTx.executeWithoutResult(tx -> repository.findById(row.getId()).ifPresent(n -> {
            n.setStatus(finalStatus);
            n.setErrorMessage(finalError);
            if (finalStatus == NotificationStatus.SENT) {
                n.setSentAt(clock.instant());
            }
            repository.save(n);
        }));
        return true;
    }
}
