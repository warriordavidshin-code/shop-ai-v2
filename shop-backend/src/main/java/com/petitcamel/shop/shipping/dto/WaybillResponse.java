package com.petitcamel.shop.shipping.dto;

/** @param printUrl label PDF / print page returned by the vendor, if any */
public record WaybillResponse(
        String trackingNumber,
        String printUrl,
        String message
) {
}
