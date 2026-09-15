package com.petitcamel.shop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 32)
        String loginId,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100)
        String name
) {
}
