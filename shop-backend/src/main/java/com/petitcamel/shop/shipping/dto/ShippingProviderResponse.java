package com.petitcamel.shop.shipping.dto;

import java.util.List;

/**
 * Vendor row for the admin settings page (never includes secrets).
 *
 * @param configured            API key / endpoint present in the environment
 * @param preferred             named in SHIPPING_PROVIDER
 * @param supportedCapabilities capabilities the Java client implements
 * @param activeCapabilities    capabilities this vendor currently serves
 */
public record ShippingProviderResponse(
        String code,
        String name,
        boolean enabled,
        boolean trackingEnabled,
        boolean waybillEnabled,
        boolean pickupEnabled,
        boolean returnPickupEnabled,
        boolean configured,
        boolean preferred,
        List<String> supportedCapabilities,
        List<String> activeCapabilities
) {
}
