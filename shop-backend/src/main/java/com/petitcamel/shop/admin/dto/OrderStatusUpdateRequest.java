package com.petitcamel.shop.admin.dto;

import com.petitcamel.shop.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateRequest(
        @NotNull OrderStatus status
) {
}
