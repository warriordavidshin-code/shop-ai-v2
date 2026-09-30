package com.petitcamel.shop.shipping.event;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentNotificationListenerTest {

    @Test
    void firstMoveIntoTransitIsDispatch() {
        assertThat(ShipmentNotificationListener.messageFor(event(ShipmentStatus.PREPARING, ShipmentStatus.IN_TRANSIT)))
                .isEqualTo("상품이 발송되었습니다.");
        assertThat(ShipmentNotificationListener.messageFor(event(ShipmentStatus.PICKUP_REQUESTED, ShipmentStatus.PICKED_UP)))
                .isEqualTo("상품이 발송되었습니다.");
    }

    @Test
    void laterTransitStepsAreNotDispatchedAgain() {
        assertThat(ShipmentNotificationListener.messageFor(event(ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT)))
                .isNull();
    }

    @Test
    void deliveredMessage() {
        assertThat(ShipmentNotificationListener.messageFor(event(ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED)))
                .isEqualTo("상품 배송이 완료되었습니다.");
        assertThat(ShipmentNotificationListener.messageFor(event(ShipmentStatus.PREPARING, ShipmentStatus.DELIVERED)))
                .isEqualTo("상품 배송이 완료되었습니다.");
    }

    private static ShipmentStatusChangedEvent event(ShipmentStatus from, ShipmentStatus to) {
        return new ShipmentStatusChangedEvent(1L, 1L, "PC1", 1L, ShipmentType.DELIVERY, from, to,
                "HANJIN", "1234********", Instant.now());
    }
}
