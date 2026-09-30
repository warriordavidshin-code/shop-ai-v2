package com.petitcamel.shop.shipping.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentStatusTest {

    @Test
    void deliveryMovesForwardOnly() {
        assertThat(ShipmentStatus.PREPARING.canAdvanceTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canAdvanceTo(ShipmentStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canAdvanceTo(ShipmentStatus.PICKED_UP)).isFalse();
        assertThat(ShipmentStatus.IN_TRANSIT.canAdvanceTo(ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(ShipmentStatus.DELIVERED.canAdvanceTo(ShipmentStatus.OUT_FOR_DELIVERY)).isFalse();
    }

    @Test
    void directionsDoNotMix() {
        assertThat(ShipmentStatus.PREPARING.canAdvanceTo(ShipmentStatus.RETURN_IN_TRANSIT)).isFalse();
        assertThat(ShipmentStatus.RETURN_REQUESTED.canAdvanceTo(ShipmentStatus.DELIVERED)).isFalse();
        assertThat(ShipmentStatus.RETURN_PICKUP_REQUESTED.canAdvanceTo(ShipmentStatus.RETURN_IN_TRANSIT)).isTrue();
    }

    @Test
    void cancelOnlyBeforePickup() {
        assertThat(ShipmentStatus.PREPARING.canAdvanceTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.PICKUP_REQUESTED.canAdvanceTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.PICKED_UP.canAdvanceTo(ShipmentStatus.CANCELLED)).isFalse();
        assertThat(ShipmentStatus.RETURN_REQUESTED.canAdvanceTo(ShipmentStatus.CANCELLED)).isFalse();
        assertThat(ShipmentStatus.CANCELLED.canAdvanceTo(ShipmentStatus.PREPARING)).isFalse();
    }

    @Test
    void koreanLabels() {
        assertThat(ShipmentStatus.PICKED_UP.getLabel()).isEqualTo("집하완료");
        assertThat(ShipmentStatus.OUT_FOR_DELIVERY.getLabel()).isEqualTo("배송출발");
        assertThat(ShipmentStatus.DELIVERED.getLabel()).isEqualTo("배송완료");
    }
}
