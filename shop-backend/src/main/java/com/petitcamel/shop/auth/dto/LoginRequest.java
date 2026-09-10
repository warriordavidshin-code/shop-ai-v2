package com.petitcamel.shop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 255)
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
    public LoginRequest {
        loginId = loginId == null ? null : loginId.trim();
    }
}
