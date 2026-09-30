package com.petitcamel.shop.shipping.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkInvoiceRequest(
        @NotEmpty(message = "등록할 송장이 없습니다.") @Size(max = 500, message = "한 번에 500건까지 등록할 수 있습니다.")
        List<@Valid Item> items
) {

    /** Same columns as the CSV upload: orderNumber, deliveryCompany, trackingNumber. */
    public record Item(
            @Size(max = 40) String orderNumber,
            @Size(max = 50) String deliveryCompany,
            @Size(max = 100) String trackingNumber
    ) {
    }
}
