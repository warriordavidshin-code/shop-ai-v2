package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ReturnStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for the admin return actions. */
public final class ReturnActionRequests {

    private ReturnActionRequests() {
    }

    public record Reject(
            @NotBlank(message = "거절 사유를 입력해 주세요.") @Size(max = 500) String reason
    ) {
    }

    /** Company and invoice are optional: a manual pickup may get its invoice number later. */
    public record Pickup(
            @Size(max = 50) String deliveryCompany,
            @Size(max = 100) String trackingNumber
    ) {
    }

    public record StatusChange(
            @NotNull ReturnStatus status
    ) {
    }

    public record Refund(
            boolean restock,
            @Size(max = 500) String adminMemo
    ) {
    }
}
