package com.petitcamel.shop.inventory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryAdjustTest {

    @Test
    void applyAdjustmentIncreasesStock() {
        Inventory inventory = inventory(10, 2);
        inventory.applyAdjustment(5);
        assertThat(inventory.getStockQuantity()).isEqualTo(15);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(13);
    }

    @Test
    void applyAdjustmentThatWouldGoNegativeFails() {
        Inventory inventory = inventory(5, 0);
        assertThatThrownBy(() -> inventory.applyAdjustment(-6))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0 미만");
        assertThat(inventory.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void applyAdjustmentThatWouldMakeReservedExceedStockFails() {
        Inventory inventory = inventory(10, 8);
        assertThatThrownBy(() -> inventory.applyAdjustment(-3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("예약");
        assertThat(inventory.getStockQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isEqualTo(8);
    }

    @Test
    void getAvailableQuantityNeverNegative() {
        Inventory inventory = inventory(3, 5);
        assertThat(inventory.getAvailableQuantity()).isZero();
    }

    private Inventory inventory(int stock, int reserved) {
        Inventory inventory = new Inventory();
        inventory.setSkuId(1L);
        inventory.setStockQuantity(stock);
        inventory.setReservedQuantity(reserved);
        inventory.setReorderPoint(5);
        inventory.setVersion(0L);
        return inventory;
    }
}
