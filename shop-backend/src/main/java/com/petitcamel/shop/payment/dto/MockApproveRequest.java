package com.petitcamel.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record MockApproveRequest(
        @NotBlank String orderNo
) {
}
