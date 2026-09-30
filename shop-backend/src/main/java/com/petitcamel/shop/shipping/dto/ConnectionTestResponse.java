package com.petitcamel.shop.shipping.dto;

public record ConnectionTestResponse(
        String providerCode,
        boolean success,
        String message
) {
}
