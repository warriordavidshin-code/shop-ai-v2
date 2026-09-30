package com.petitcamel.shop.shipping.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentStatusTest {

    @Test
    void movesForwardOnly() {
        assertThat(ShipmentStatus.READY.canAdvanceTo(ShipmentStatus.WAYBILL_ISSUED)).isTrue();
        assertThat(ShipmentStatus.READY.canAdvanceTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canAdvanceTo(ShipmentStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canAdvanceTo(ShipmentStatus.PICKED_UP)).isFalse();
        assertThat(ShipmentStatus.IN_TRANSIT.canAdvanceTo(ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(ShipmentStatus.DELIVERED.canAdvanceTo(ShipmentStatus.OUT_FOR_DELIVERY)).isFalse();
    }

    @Test
    void cancelOnlyBeforePickup() {
        assertThat(ShipmentStatus.READY.canAdvanceTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.PICKUP_REQUESTED.canAdvanceTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.PICKED_UP.canAdvanceTo(ShipmentStatus.CANCELLED)).isFalse();
        assertThat(ShipmentStatus.CANCELLED.canAdvanceTo(ShipmentStatus.READY)).isFalse();
    }

    @Test
    void failedFromAnyOpenStatus() {
        assertThat(ShipmentStatus.OUT_FOR_DELIVERY.canAdvanceTo(ShipmentStatus.FAILED)).isTrue();
        assertThat(ShipmentStatus.DELIVERED.canAdvanceTo(ShipmentStatus.FAILED)).isFalse();
        assertThat(ShipmentStatus.FAILED.isFinal()).isTrue();
    }

    @Test
    void labelsDependOnDirection() {
        assertThat(ShipmentStatus.PICKED_UP.labelFor(ShipmentType.DELIVERY)).isEqualTo("집하완료");
        assertThat(ShipmentStatus.DELIVERED.labelFor(ShipmentType.DELIVERY)).isEqualTo("배송완료");
        assertThat(ShipmentStatus.PICKUP_REQUESTED.labelFor(ShipmentType.RETURN)).isEqualTo("반품수거요청");
        assertThat(ShipmentStatus.DELIVERED.labelFor(ShipmentType.RETURN)).isEqualTo("반품도착");
        assertThat(ShipmentType.RETURN.isInbound()).isTrue();
        assertThat(ShipmentType.DELIVERY.isInbound()).isFalse();
    }

    @Test
    void changeStatusFillsMilestonesOnce() {
        Shipment shipment = new Shipment();
        shipment.setStatus(ShipmentStatus.READY);
        Instant t1 = Instant.parse("2026-09-29T01:00:00Z");
        Instant t2 = Instant.parse("2026-09-29T05:00:00Z");
        Instant t3 = Instant.parse("2026-09-30T03:00:00Z");

        shipment.changeStatus(ShipmentStatus.PICKED_UP, t1);
        shipment.changeStatus(ShipmentStatus.IN_TRANSIT, t2);
        shipment.changeStatus(ShipmentStatus.DELIVERED, t3);

        assertThat(shipment.getPickedUpAt()).isEqualTo(t1);
        assertThat(shipment.getShippedAt()).isEqualTo(t1);
        assertThat(shipment.getDeliveredAt()).isEqualTo(t3);
        assertThat(shipment.getLastStatusChangedAt()).isEqualTo(t3);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }
}
