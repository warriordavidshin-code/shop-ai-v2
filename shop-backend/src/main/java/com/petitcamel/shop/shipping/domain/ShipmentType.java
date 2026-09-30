package com.petitcamel.shop.shipping.domain;

/**
 * What a physical movement is for. Statuses are shared by all types; the type says which way the parcel goes.
 * EXCHANGE_* are reserved for the exchange flow and not created yet.
 */
public enum ShipmentType {
    DELIVERY,
    RETURN,
    EXCHANGE_RETURN,
    EXCHANGE_DELIVERY;

    /** True when the parcel travels from the customer back to the shop. */
    public boolean isInbound() {
        return this == RETURN || this == EXCHANGE_RETURN;
    }
}
