package com.petitcamel.shop.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberAddressRequest(
        @NotBlank(message = "배송지 이름을 입력해 주세요.")
        @Size(max = 50, message = "배송지 이름은 50자 이하로 입력해 주세요.")
        String label,

        @NotBlank(message = "받는 분 이름을 입력해 주세요.")
        @Size(max = 100)
        String receiverName,

        @NotBlank(message = "받는 분 연락처를 입력해 주세요.")
        @Pattern(regexp = "^[0-9+\\-\\s]{9,32}$", message = "연락처 형식이 올바르지 않습니다.")
        String receiverPhone,

        @NotBlank(message = "우편번호를 입력해 주세요.")
        @Size(max = 16)
        String postcode,

        @NotBlank(message = "기본주소를 입력해 주세요.")
        @Size(max = 255)
        String address1,

        @Size(max = 255)
        String address2,

        boolean defaultAddress
) {
}
