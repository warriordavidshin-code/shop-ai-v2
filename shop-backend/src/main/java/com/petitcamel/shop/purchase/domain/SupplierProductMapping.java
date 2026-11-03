package com.petitcamel.shop.purchase.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Remembers which internal SKU a supplier's product code / name refers to (learned from confirmed matches). */
@Entity
@Table(name = "supplier_product_mapping")
public class SupplierProductMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_product_mapping_id")
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_product_code", length = 100)
    private String supplierProductCode;

    @Column(name = "supplier_product_name", length = 200)
    private String supplierProductName;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "size", length = 30)
    private String size;

    @Column(name = "last_unit_cost")
    private Long lastUnitCost;

    @Column(name = "last_purchase_date")
    private LocalDate lastPurchaseDate;

    public Long getId() {
        return id;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierProductCode() {
        return supplierProductCode;
    }

    public void setSupplierProductCode(String supplierProductCode) {
        this.supplierProductCode = supplierProductCode;
    }

    public String getSupplierProductName() {
        return supplierProductName;
    }

    public void setSupplierProductName(String supplierProductName) {
        this.supplierProductName = supplierProductName;
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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Long getLastUnitCost() {
        return lastUnitCost;
    }

    public void setLastUnitCost(Long lastUnitCost) {
        this.lastUnitCost = lastUnitCost;
    }

    public LocalDate getLastPurchaseDate() {
        return lastPurchaseDate;
    }

    public void setLastPurchaseDate(LocalDate lastPurchaseDate) {
        this.lastPurchaseDate = lastPurchaseDate;
    }
}
