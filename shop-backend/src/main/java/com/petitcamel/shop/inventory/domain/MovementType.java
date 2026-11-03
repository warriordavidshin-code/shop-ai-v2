package com.petitcamel.shop.inventory.domain;

public enum MovementType {
    RECEIPT,
    SALE,
    CANCEL,
    RETURN,
    ADJUSTMENT,
    RESERVATION,
    RELEASE,
    /** Damaged or discarded stock written off. */
    DAMAGE
}
