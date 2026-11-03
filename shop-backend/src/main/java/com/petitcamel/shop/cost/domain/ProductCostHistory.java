package com.petitcamel.shop.cost.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable record of one confirmed receipt line's cost. */
@Entity
@Table(name = "product_cost_history")
public class ProductCostHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_cost_history_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "purchase_receipt_item_id")
    private Long purchaseReceiptItemId;

    @Column(name = "purchase_ledger_id")
    private Long purchaseLedgerId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "allocated_unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal allocatedUnitCost = BigDecimal.ZERO;

    @Column(name = "landed_unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal landedUnitCost;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "avg_cost_before", precision = 15, scale = 4)
    private BigDecimal avgCostBefore;

    @Column(name = "avg_cost_after", precision = 15, scale = 4)
    private BigDecimal avgCostAfter;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Long getPurchaseReceiptItemId() {
        return purchaseReceiptItemId;
    }

    public void setPurchaseReceiptItemId(Long purchaseReceiptItemId) {
        this.purchaseReceiptItemId = purchaseReceiptItemId;
    }

    public Long getPurchaseLedgerId() {
        return purchaseLedgerId;
    }

    public void setPurchaseLedgerId(Long purchaseLedgerId) {
        this.purchaseLedgerId = purchaseLedgerId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getAllocatedUnitCost() {
        return allocatedUnitCost;
    }

    public void setAllocatedUnitCost(BigDecimal allocatedUnitCost) {
        this.allocatedUnitCost = allocatedUnitCost;
    }

    public BigDecimal getLandedUnitCost() {
        return landedUnitCost;
    }

    public void setLandedUnitCost(BigDecimal landedUnitCost) {
        this.landedUnitCost = landedUnitCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getAvgCostBefore() {
        return avgCostBefore;
    }

    public void setAvgCostBefore(BigDecimal avgCostBefore) {
        this.avgCostBefore = avgCostBefore;
    }

    public BigDecimal getAvgCostAfter() {
        return avgCostAfter;
    }

    public void setAvgCostAfter(BigDecimal avgCostAfter) {
        this.avgCostAfter = avgCostAfter;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
