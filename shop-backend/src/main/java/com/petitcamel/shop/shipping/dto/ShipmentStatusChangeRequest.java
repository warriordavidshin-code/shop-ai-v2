package com.petitcamel.shop.shipping.dto;

import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import jakarta.validation.constraints.NotNull;

public record ShipmentStatusChangeRequest(
        @NotNull ShipmentStatus status
) {
}
