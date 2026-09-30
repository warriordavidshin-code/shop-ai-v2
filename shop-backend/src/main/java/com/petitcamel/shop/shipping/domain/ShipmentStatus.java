package com.petitcamel.shop.shipping.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Progress of one physical movement, shared by every {@link ShipmentType} (a return in transit is
 * {@code RETURN + IN_TRANSIT}, not a separate status). {@code rank} orders the forward flow so tracking updates
 * only ever move a shipment forward; CANCELLED and FAILED are terminal side exits.
 */
public enum ShipmentStatus {
    READY("상품준비중", "반품접수", 10),
    WAYBILL_ISSUED("송장발급", "반품송장발급", 20),
    PICKUP_REQUESTED("집하요청", "반품수거요청", 30),
    PICKED_UP("집하완료", "반품수거완료", 40),
    IN_TRANSIT("배송중", "반품배송중", 50),
    OUT_FOR_DELIVERY("배송출발", "반품입고중", 60),
    DELIVERED("배송완료", "반품도착", 70),
    CANCELLED("배송취소", "반품취소", 0),
    FAILED("배송실패", "반품실패", 0);

    /** Statuses polled by the tracking scheduler (an invoice exists and the parcel is not settled). */
    public static final Set<ShipmentStatus> TRACKABLE =
            EnumSet.of(WAYBILL_ISSUED, PICKUP_REQUESTED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY);

    private final String outboundLabel;
    private final String inboundLabel;
    private final int rank;

    ShipmentStatus(String outboundLabel, String inboundLabel, int rank) {
        this.outboundLabel = outboundLabel;
        this.inboundLabel = inboundLabel;
        this.rank = rank;
    }

    public String labelFor(ShipmentType type) {
        return type != null && type.isInbound() ? inboundLabel : outboundLabel;
    }

    public int getRank() {
        return rank;
    }

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED || this == FAILED;
    }

    /** True once the courier has the parcel (picked up, moving or delivered). */
    public boolean isMoving() {
        return rank >= PICKED_UP.rank;
    }

    /**
     * Forward-only transitions. CANCELLED is allowed until the courier picks the parcel up; FAILED from any
     * unsettled status.
     */
    public boolean canAdvanceTo(ShipmentStatus next) {
        if (next == null || next == this || isFinal()) {
            return false;
        }
        if (next == CANCELLED) {
            return rank < PICKED_UP.rank;
        }
        if (next == FAILED) {
            return true;
        }
        return next.rank > rank;
    }
}
