package com.petitcamel.shop.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param deliveryCompany internal code (HANJIN) or company name (한진택배) */
public record InvoiceRegisterRequest(
        @NotBlank(message = "택배사를 선택해 주세요.") @Size(max = 50) String deliveryCompany,
        @NotBlank(message = "송장번호를 입력해 주세요.") @Size(max = 100) String trackingNumber
) {
}
