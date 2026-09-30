package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ExtraAreaType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param extraFee area-specific surcharge; null uses the policy fee for the area type */
public record ExtraAreaRequest(
        @NotNull ExtraAreaType areaType,
        @NotBlank(message = "지역명을 입력해 주세요.") @Size(max = 100) String areaName,
        @NotNull @Pattern(regexp = "\\d{5}", message = "우편번호는 숫자 5자리입니다.") String postalCodeFrom,
        @NotNull @Pattern(regexp = "\\d{5}", message = "우편번호는 숫자 5자리입니다.") String postalCodeTo,
        @Min(0) @Max(1_000_000) Long extraFee
) {
}
