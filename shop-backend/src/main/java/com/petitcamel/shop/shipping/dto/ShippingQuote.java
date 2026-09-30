package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ExtraAreaType;

import java.math.BigDecimal;

/**
 * @param baseFee     base fee after the free-shipping rule (0 when free)
 * @param extraFee    Jeju / remote-island surcharge, charged even with free shipping
 * @param deliveryFee baseFee + extraFee
 * @param areaType    surcharge area of the postcode, or null
 */
public record ShippingQuote(
        BigDecimal productAmount,
        BigDecimal baseFee,
        BigDecimal extraFee,
        BigDecimal deliveryFee,
        boolean freeShipping,
        ExtraAreaType areaType,
        BigDecimal freeShippingAmount
) {
}
