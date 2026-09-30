package com.petitcamel.shop.shipping.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Business state of a return request. Parcel details (picked up, in transit, arrived) are on the RETURN shipment;
 * IN_PROGRESS covers the whole courier leg.
 */
public enum ReturnStatus {
    REQUESTED("반품신청"),
    APPROVED("반품승인"),
    PICKUP_REQUESTED("반품수거요청"),
    IN_PROGRESS("반품배송중"),
    RECEIVED("반품입고"),
    COMPLETED("반품완료"),
    REJECTED("반품거절"),
    CANCELLED("반품철회");

    /** Returns that still need admin work (dashboard "반품 요청"). */
    public static final Set<ReturnStatus> OPEN =
            EnumSet.of(REQUESTED, APPROVED, PICKUP_REQUESTED, IN_PROGRESS, RECEIVED);

    /** Ended without a return; the order can be returned again. */
    public static final Set<ReturnStatus> WITHDRAWN = EnumSet.of(REJECTED, CANCELLED);

    /** The customer can still withdraw the request (nothing has been picked up). */
    public static final Set<ReturnStatus> CUSTOMER_CANCELLABLE = EnumSet.of(REQUESTED, APPROVED);

    private final String label;

    ReturnStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
