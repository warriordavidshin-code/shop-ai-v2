package com.petitcamel.shop.shipping.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ReturnStatus {
    REQUESTED("반품신청"),
    APPROVED("반품승인"),
    PICKUP_REQUESTED("반품수거요청"),
    PICKED_UP("기사방문수거"),
    IN_TRANSIT("반품배송중"),
    RECEIVED("반품입고"),
    REFUNDED("환불완료"),
    REJECTED("반품거절");

    /** Returns that still need admin work (dashboard "반품 요청"). */
    public static final Set<ReturnStatus> OPEN = EnumSet.of(
            REQUESTED, APPROVED, PICKUP_REQUESTED, PICKED_UP, IN_TRANSIT, RECEIVED);

    private final String label;

    ReturnStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
