package com.petitcamel.shop.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "inventory_movement")
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long movementId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "actor_member_id")
    private Long actorMemberId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "stock_before")
    private Integer stockBefore;

    @Column(name = "stock_after")
    private Integer stockAfter;

    @Column(name = "purchase_receipt_id")
    private Long purchaseReceiptId;

    @Column(name = "unit_cost", precision = 15, scale = 4)
    private java.math.BigDecimal unitCost;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getStockBefore() {
        return stockBefore;
    }

    public void setStockBefore(Integer stockBefore) {
        this.stockBefore = stockBefore;
    }

    public Integer getStockAfter() {
        return stockAfter;
    }

    public void setStockAfter(Integer stockAfter) {
        this.stockAfter = stockAfter;
    }

    public Long getPurchaseReceiptId() {
        return purchaseReceiptId;
    }

    public void setPurchaseReceiptId(Long purchaseReceiptId) {
        this.purchaseReceiptId = purchaseReceiptId;
    }

    public java.math.BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(java.math.BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public Long getMovementId() {
        return movementId;
    }

    public void setMovementId(Long movementId) {
        this.movementId = movementId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getActorMemberId() {
        return actorMemberId;
    }

    public void setActorMemberId(Long actorMemberId) {
        this.actorMemberId = actorMemberId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
