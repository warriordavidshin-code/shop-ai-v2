package com.petitcamel.shop.shipping.provider;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingStatusMapperTest {

    @Test
    void mapsCourierWordingToInternalStatus() {
        assertThat(TrackingStatusMapper.fromText("상품인수")).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(TrackingStatusMapper.fromText("집화처리")).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(TrackingStatusMapper.fromText("이동중")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(TrackingStatusMapper.fromText("간선상차")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(TrackingStatusMapper.fromText("배달출발")).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(TrackingStatusMapper.fromText("배달 완료")).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(TrackingStatusMapper.fromText("알 수 없음")).isNull();
        assertThat(TrackingStatusMapper.fromText(null)).isNull();
    }

    @Test
    void mapsSweetTrackerLevels() {
        assertThat(TrackingStatusMapper.fromSweetTrackerLevel(1)).isNull();
        assertThat(TrackingStatusMapper.fromSweetTrackerLevel(2)).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(TrackingStatusMapper.fromSweetTrackerLevel(3)).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(TrackingStatusMapper.fromSweetTrackerLevel(4)).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(TrackingStatusMapper.fromSweetTrackerLevel(5)).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(TrackingStatusMapper.fromSweetTrackerLevel(6)).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void convertsToReturnDirection() {
        assertThat(TrackingStatusMapper.toReturnStatus(ShipmentStatus.PICKED_UP)).isEqualTo(ShipmentStatus.RETURN_IN_TRANSIT);
        assertThat(TrackingStatusMapper.toReturnStatus(ShipmentStatus.OUT_FOR_DELIVERY)).isEqualTo(ShipmentStatus.RETURN_IN_TRANSIT);
        assertThat(TrackingStatusMapper.toReturnStatus(ShipmentStatus.DELIVERED)).isEqualTo(ShipmentStatus.RETURN_COMPLETED);
        assertThat(TrackingStatusMapper.toReturnStatus(null)).isNull();
    }
}
