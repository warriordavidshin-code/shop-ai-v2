package com.petitcamel.shop.shipping.waybill;

/** @param printUrl label PDF/print page returned by the provider, if any */
public record WaybillResponse(
        String trackingNumber,
        String printUrl,
        String message
) {
}
