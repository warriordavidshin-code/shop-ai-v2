package com.petitcamel.shop.shipping.dto;

import java.util.Map;

/** @param providerCodes provider name -> that provider's courier code */
public record DeliveryCompanyResponse(
        String code,
        String companyName,
        String trackingUrlTemplate,
        boolean enabled,
        int sortOrder,
        Map<String, String> providerCodes
) {
}
