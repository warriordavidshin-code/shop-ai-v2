package com.petitcamel.shop.shipping.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Shipment progress. {@code rank} orders the statuses of one direction so that tracking
 * updates only ever move a shipment forward.
 */
public enum ShipmentStatus {
    PREPARING("상품준비중", 10, ShipmentType.DELIVERY),
    READY("배송준비", 20, ShipmentType.DELIVERY),
    PICKUP_REQUESTED("수거요청", 30, ShipmentType.DELIVERY),
    PICKED_UP("집하완료", 40, ShipmentType.DELIVERY),
    IN_TRANSIT("배송중", 50, ShipmentType.DELIVERY),
    OUT_FOR_DELIVERY("배송출발", 60, ShipmentType.DELIVERY),
    DELIVERED("배송완료", 70, ShipmentType.DELIVERY),
    RETURN_REQUESTED("반품요청", 10, ShipmentType.RETURN),
    RETURN_PICKUP_REQUESTED("반품수거요청", 20, ShipmentType.RETURN),
    RETURN_IN_TRANSIT("반품배송중", 30, ShipmentType.RETURN),
    RETURN_COMPLETED("반품입고", 40, ShipmentType.RETURN),
    CANCELLED("배송취소", 0, null);

    /** Statuses polled by the tracking scheduler. */
    public static final Set<ShipmentStatus> TRACKABLE = EnumSet.of(
            PICKUP_REQUESTED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY,
            RETURN_PICKUP_REQUESTED, RETURN_IN_TRANSIT);

    private final String label;
    private final int rank;
    private final ShipmentType direction;

    ShipmentStatus(String label, int rank, ShipmentType direction) {
        this.label = label;
        this.rank = rank;
        this.direction = direction;
    }

    public String getLabel() {
        return label;
    }

    public int getRank() {
        return rank;
    }

    public boolean belongsTo(ShipmentType type) {
        return direction == type;
    }

    public boolean isFinal() {
        return this == DELIVERED || this == RETURN_COMPLETED || this == CANCELLED;
    }

    /** True when moving to {@code next} does not go backwards within the same direction. */
    public boolean canAdvanceTo(ShipmentStatus next) {
        if (next == null || next == this || isFinal()) {
            return false;
        }
        if (next == CANCELLED) {
            return rank < PICKED_UP.rank && direction == ShipmentType.DELIVERY;
        }
        return next.direction == direction && next.rank > rank;
    }
}
