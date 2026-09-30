package com.petitcamel.shop.shipping.dto;

import java.util.Map;

/** @param providerCodes vendor code -> that vendor's code for this courier */
public record DeliveryCompanyResponse(
        String code,
        String companyName,
        String trackingUrlTemplate,
        boolean enabled,
        int sortOrder,
        Map<String, String> providerCodes
) {
}
