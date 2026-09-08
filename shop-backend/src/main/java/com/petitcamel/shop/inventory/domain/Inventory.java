package com.petitcamel.shop.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long inventoryId;

    @Column(name = "sku_id", nullable = false, unique = true)
    private Long skuId;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint = 5;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public Integer getReorderPoint() {
        return reorderPoint;
    }

    public void setReorderPoint(Integer reorderPoint) {
        this.reorderPoint = reorderPoint;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public int getAvailableQuantity() {
        int stock = stockQuantity == null ? 0 : stockQuantity;
        int reserved = reservedQuantity == null ? 0 : reservedQuantity;
        return Math.max(0, stock - reserved);
    }

    /**
     * Applies a stock adjustment delta. Rejects outcomes where stock would be negative
     * or reserved would fall outside {@code [0, stock]}.
     */
    public void applyAdjustment(int delta) {
        int currentStock = stockQuantity == null ? 0 : stockQuantity;
        int reserved = reservedQuantity == null ? 0 : reservedQuantity;
        int newStock = currentStock + delta;
        if (newStock < 0) {
            throw new IllegalStateException("재고 수량은 0 미만이 될 수 없습니다.");
        }
        if (reserved < 0 || reserved > newStock) {
            throw new IllegalStateException("예약 수량이 재고 범위를 벗어날 수 없습니다.");
        }
        this.stockQuantity = newStock;
    }

    /** Increases reserved quantity without changing stock (order placed, payment pending). */
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("예약 수량은 1 이상이어야 합니다.");
        }
        if (getAvailableQuantity() < quantity) {
            throw new IllegalStateException("가용 재고가 부족합니다.");
        }
        int reserved = reservedQuantity == null ? 0 : reservedQuantity;
        this.reservedQuantity = reserved + quantity;
    }

    /** Decreases reserved quantity (payment-pending cancel). */
    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("해제 수량은 1 이상이어야 합니다.");
        }
        int reserved = reservedQuantity == null ? 0 : reservedQuantity;
        if (reserved < quantity) {
            throw new IllegalStateException("예약 수량이 부족합니다.");
        }
        this.reservedQuantity = reserved - quantity;
    }

    /** Confirms sale: deducts stock and reserved by the same quantity. */
    public void confirmSale(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("판매 수량은 1 이상이어야 합니다.");
        }
        int stock = stockQuantity == null ? 0 : stockQuantity;
        int reserved = reservedQuantity == null ? 0 : reservedQuantity;
        if (stock < quantity) {
            throw new IllegalStateException("재고 수량이 부족합니다.");
        }
        if (reserved < quantity) {
            throw new IllegalStateException("예약 수량이 부족합니다.");
        }
        this.stockQuantity = stock - quantity;
        this.reservedQuantity = reserved - quantity;
    }

    /** Restores stock after a paid order is cancelled. */
    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복구 수량은 1 이상이어야 합니다.");
        }
        int stock = stockQuantity == null ? 0 : stockQuantity;
        this.stockQuantity = stock + quantity;
    }
}
