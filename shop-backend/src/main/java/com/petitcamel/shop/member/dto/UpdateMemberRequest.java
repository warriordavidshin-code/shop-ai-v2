package com.petitcamel.shop.member.dto;

import com.petitcamel.shop.member.domain.Gender;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateMemberRequest(
        @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다.")
        String name,

        @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
        LocalDate birthDate,

        Gender gender,

        @Size(max = 32)
        String phone,

        @Size(max = 16)
        String postcode,

        @Size(max = 255)
        String address1,

        @Size(max = 255)
        String address2
) {
}
