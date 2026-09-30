package com.petitcamel.shop.shipping.event;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentNotificationListenerTest {

    @Test
    void firstMoveIntoTransitIsDispatch() {
        assertThat(event(ShipmentType.DELIVERY, ShipmentStatus.READY, ShipmentStatus.IN_TRANSIT).notificationType())
                .isEqualTo(ShipmentStatusChangedEvent.DELIVERY_DISPATCHED);
        assertThat(event(ShipmentType.DELIVERY, ShipmentStatus.PICKUP_REQUESTED, ShipmentStatus.PICKED_UP).notificationType())
                .isEqualTo(ShipmentStatusChangedEvent.DELIVERY_DISPATCHED);
        assertThat(ShipmentNotificationListener.messageFor(ShipmentStatusChangedEvent.DELIVERY_DISPATCHED))
                .isEqualTo("상품이 발송되었습니다.");
    }

    @Test
    void laterTransitStepsAreNotDispatchedAgain() {
        assertThat(event(ShipmentType.DELIVERY, ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT).notificationType())
                .isNull();
        assertThat(event(ShipmentType.DELIVERY, ShipmentStatus.READY, ShipmentStatus.WAYBILL_ISSUED).notificationType())
                .isNull();
    }

    @Test
    void deliveredMessage() {
        assertThat(event(ShipmentType.DELIVERY, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED).notificationType())
                .isEqualTo(ShipmentStatusChangedEvent.DELIVERY_DELIVERED);
        assertThat(event(ShipmentType.DELIVERY, ShipmentStatus.READY, ShipmentStatus.DELIVERED).notificationType())
                .isEqualTo(ShipmentStatusChangedEvent.DELIVERY_DELIVERED);
        assertThat(ShipmentNotificationListener.messageFor(ShipmentStatusChangedEvent.DELIVERY_DELIVERED))
                .isEqualTo("상품 배송이 완료되었습니다.");
    }

    @Test
    void returnDirectionUsesReturnMessages() {
        assertThat(event(ShipmentType.RETURN, ShipmentStatus.READY, ShipmentStatus.PICKUP_REQUESTED).notificationType())
                .isEqualTo(ShipmentStatusChangedEvent.RETURN_PICKUP_REQUESTED);
        assertThat(event(ShipmentType.RETURN, ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED).notificationType())
                .isEqualTo(ShipmentStatusChangedEvent.RETURN_RECEIVED);
        assertThat(event(ShipmentType.RETURN, ShipmentStatus.PICKUP_REQUESTED, ShipmentStatus.PICKED_UP).notificationType())
                .isNull();
    }

    private static ShipmentStatusChangedEvent event(ShipmentType type, ShipmentStatus from, ShipmentStatus to) {
        return new ShipmentStatusChangedEvent(1L, 1L, "PC1", 1L, type, from, to,
                "HANJIN", "1234********", Instant.now());
    }
}
