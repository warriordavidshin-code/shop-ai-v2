package com.petitcamel.shop.shipping.dto;

/** Partial update; null fields are left unchanged. */
public record ShippingProviderUpdateRequest(
        Boolean enabled,
        Boolean trackingEnabled,
        Boolean waybillEnabled,
        Boolean pickupEnabled,
        Boolean returnPickupEnabled
) {
}
