package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ReturnReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReturnCreateRequest(
        @NotNull(message = "반품 사유를 선택해 주세요.") ReturnReason returnReason,
        @Size(max = 1000, message = "상세 내용은 1000자 이내로 입력해 주세요.") String returnMemo,
        @NotBlank(message = "수거 담당자 이름을 입력해 주세요.") @Size(max = 100) String pickupName,
        @NotBlank(message = "수거 연락처를 입력해 주세요.")
        @Pattern(regexp = "^[0-9\\-+() ]{8,20}$", message = "연락처 형식을 확인해 주세요.")
        String pickupPhone,
        @NotBlank(message = "우편번호를 입력해 주세요.") @Size(max = 16) String pickupPostcode,
        @NotBlank(message = "수거 주소를 입력해 주세요.") @Size(max = 255) String pickupAddress,
        @Size(max = 255) String pickupAddressDetail
) {
}
