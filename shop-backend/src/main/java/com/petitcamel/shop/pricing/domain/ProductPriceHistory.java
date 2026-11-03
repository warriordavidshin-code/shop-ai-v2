package com.petitcamel.shop.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_price_history")
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_price_history_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "normal_price_before", precision = 15, scale = 2)
    private BigDecimal normalPriceBefore;

    @Column(name = "normal_price_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal normalPriceAfter;

    @Column(name = "sale_price_before", precision = 15, scale = 2)
    private BigDecimal salePriceBefore;

    @Column(name = "sale_price_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal salePriceAfter;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getNormalPriceBefore() {
        return normalPriceBefore;
    }

    public void setNormalPriceBefore(BigDecimal normalPriceBefore) {
        this.normalPriceBefore = normalPriceBefore;
    }

    public BigDecimal getNormalPriceAfter() {
        return normalPriceAfter;
    }

    public void setNormalPriceAfter(BigDecimal normalPriceAfter) {
        this.normalPriceAfter = normalPriceAfter;
    }

    public BigDecimal getSalePriceBefore() {
        return salePriceBefore;
    }

    public void setSalePriceBefore(BigDecimal salePriceBefore) {
        this.salePriceBefore = salePriceBefore;
    }

    public BigDecimal getSalePriceAfter() {
        return salePriceAfter;
    }

    public void setSalePriceAfter(BigDecimal salePriceAfter) {
        this.salePriceAfter = salePriceAfter;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Long changedBy) {
        this.changedBy = changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }
}
