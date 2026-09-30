package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ExtraAreaType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtraAreaRequest(
        @NotNull ExtraAreaType areaType,
        @NotNull @Pattern(regexp = "\\d{5}", message = "우편번호는 숫자 5자리입니다.") String postcodeFrom,
        @NotNull @Pattern(regexp = "\\d{5}", message = "우편번호는 숫자 5자리입니다.") String postcodeTo,
        @Size(max = 100) String note
) {
}
