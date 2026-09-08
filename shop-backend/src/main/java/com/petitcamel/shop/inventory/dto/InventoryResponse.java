package com.petitcamel.shop.inventory.dto;

public record InventoryResponse(
        Long inventoryId,
        Long skuId,
        int stockQuantity,
        int reservedQuantity,
        int availableQuantity,
        Long version
) {
}
