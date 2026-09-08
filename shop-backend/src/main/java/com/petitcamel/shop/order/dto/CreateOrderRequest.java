package com.petitcamel.shop.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items,
        @NotBlank @Size(max = 100) String receiverName,
        @NotBlank @Size(max = 32) String receiverPhone,
        @NotBlank @Size(max = 16) String postcode,
        @NotBlank @Size(max = 255) String address1,
        @Size(max = 255) String address2,
        @Size(max = 500) String orderMemo
) {
}
