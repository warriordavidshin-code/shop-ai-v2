package com.petitcamel.shop.shipping.domain;

/**
 * PENDING: vendor call started, result not recorded yet. UNKNOWN: the vendor may or may not have done it (timeout,
 * or a stale PENDING that could not be recovered); an admin has to check the vendor side before retrying.
 */
public enum ShippingOperationStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
