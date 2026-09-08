package com.petitcamel.shop.auth.dto;

import com.petitcamel.shop.member.domain.Gender;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255)
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 10, message = "비밀번호는 10자 이상이어야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{10,}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다.")
        String name,

        @NotNull(message = "생년월일은 필수입니다.")
        @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
        LocalDate birthDate,

        @NotNull(message = "성별은 필수입니다.")
        Gender gender,

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Size(max = 32)
        String phone,

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(max = 16)
        String postcode,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255)
        String address1,

        @Size(max = 255)
        String address2,

        @AssertTrue(message = "이용약관에 동의해야 합니다.")
        boolean termsAgreed,

        @AssertTrue(message = "개인정보 처리방침에 동의해야 합니다.")
        boolean privacyAgreed
) {
}
