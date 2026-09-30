package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ExtraAreaType;

public record ExtraAreaResponse(
        Long areaId,
        ExtraAreaType areaType,
        String postcodeFrom,
        String postcodeTo,
        String note
) {
}
