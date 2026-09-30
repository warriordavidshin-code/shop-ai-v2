package com.petitcamel.shop.shipping.dto;

import jakarta.validation.constraints.Pattern;

/** @param externalCompanyCode vendor's courier code; blank removes the mapping */
public record ProviderCodeUpdateRequest(
        @Pattern(regexp = "^[A-Za-z0-9_\\-]{0,30}$", message = "코드는 영문/숫자 30자 이내입니다.")
        String externalCompanyCode
) {
}
