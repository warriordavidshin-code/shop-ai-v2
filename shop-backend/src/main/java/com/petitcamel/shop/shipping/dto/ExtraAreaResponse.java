package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ExtraAreaSource;
import com.petitcamel.shop.shipping.domain.ExtraAreaType;

/**
 * @param extraFee     area-specific fee, or null when the policy fee applies
 * @param effectiveFee fee charged today (area fee or policy fee)
 */
public record ExtraAreaResponse(
        Long areaId,
        ExtraAreaType areaType,
        String areaName,
        String postalCodeFrom,
        String postalCodeTo,
        Long extraFee,
        long effectiveFee,
        boolean enabled,
        ExtraAreaSource source
) {
}
