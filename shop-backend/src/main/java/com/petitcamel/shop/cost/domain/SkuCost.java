package com.petitcamel.shop.cost.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/** Moving weighted-average unit cost of one SKU. */
@Entity
@Table(name = "sku_cost")
public class SkuCost {

    @Id
    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "avg_unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal avgUnitCost;

    /** Quantity the average is based on; capped by on-hand stock at each receipt. */
    @Column(name = "costed_quantity", nullable = false)
    private Integer costedQuantity = 0;

    @Column(name = "last_unit_cost", precision = 15, scale = 4)
    private BigDecimal lastUnitCost;

    @Column(name = "last_received_at")
    private Instant lastReceivedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public BigDecimal getAvgUnitCost() {
        return avgUnitCost;
    }

    public void setAvgUnitCost(BigDecimal avgUnitCost) {
        this.avgUnitCost = avgUnitCost;
    }

    public Integer getCostedQuantity() {
        return costedQuantity;
    }

    public void setCostedQuantity(Integer costedQuantity) {
        this.costedQuantity = costedQuantity;
    }

    public BigDecimal getLastUnitCost() {
        return lastUnitCost;
    }

    public void setLastUnitCost(BigDecimal lastUnitCost) {
        this.lastUnitCost = lastUnitCost;
    }

    public Instant getLastReceivedAt() {
        return lastReceivedAt;
    }

    public void setLastReceivedAt(Instant lastReceivedAt) {
        this.lastReceivedAt = lastReceivedAt;
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
}
