package com.petitcamel.shop.member.dto;

import com.petitcamel.shop.member.domain.Gender;
import com.petitcamel.shop.member.domain.MemberRole;

import java.time.LocalDate;

public record MemberResponse(
        Long memberId,
        String loginId,
        String email,
        String name,
        LocalDate birthDate,
        int age,
        Gender gender,
        String phone,
        String postcode,
        String address1,
        String address2,
        MemberRole role
) {
}
