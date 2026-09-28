package com.petitcamel.shop.member.dto;

import jakarta.validation.constraints.NotBlank;

public record ReauthPasswordRequest(
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}
