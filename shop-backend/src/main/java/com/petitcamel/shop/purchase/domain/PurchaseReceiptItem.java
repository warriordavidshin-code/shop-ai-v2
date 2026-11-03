package com.petitcamel.shop.purchase.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_receipt_item")
public class PurchaseReceiptItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_receipt_item_id")
    private Long id;

    @Column(name = "purchase_receipt_id", nullable = false)
    private Long purchaseReceiptId;

    @Column(name = "purchase_ledger_item_id")
    private Long purchaseLedgerItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "expected_quantity", nullable = false)
    private Integer expectedQuantity;

    /** Counted quantity, set on confirmation. */
    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    @Column(name = "unit_cost", nullable = false)
    private Long unitCost;

    @Column(name = "allocated_cost", precision = 15, scale = 2)
    private BigDecimal allocatedCost;

    @Column(name = "landed_unit_cost", precision = 15, scale = 4)
    private BigDecimal landedUnitCost;

    public Long getId() {
        return id;
    }

    public Long getPurchaseReceiptId() {
        return purchaseReceiptId;
    }

    public void setPurchaseReceiptId(Long purchaseReceiptId) {
        this.purchaseReceiptId = purchaseReceiptId;
    }

    public Long getPurchaseLedgerItemId() {
        return purchaseLedgerItemId;
    }

    public void setPurchaseLedgerItemId(Long purchaseLedgerItemId) {
        this.purchaseLedgerItemId = purchaseLedgerItemId;
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

    public Integer getExpectedQuantity() {
        return expectedQuantity;
    }

    public void setExpectedQuantity(Integer expectedQuantity) {
        this.expectedQuantity = expectedQuantity;
    }

    public Integer getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(Integer receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }

    public Long getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(Long unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getAllocatedCost() {
        return allocatedCost;
    }

    public void setAllocatedCost(BigDecimal allocatedCost) {
        this.allocatedCost = allocatedCost;
    }

    public BigDecimal getLandedUnitCost() {
        return landedUnitCost;
    }

    public void setLandedUnitCost(BigDecimal landedUnitCost) {
        this.landedUnitCost = landedUnitCost;
    }
}
