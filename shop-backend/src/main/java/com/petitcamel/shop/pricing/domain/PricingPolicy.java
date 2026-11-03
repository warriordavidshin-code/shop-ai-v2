package com.petitcamel.shop.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/** Single-row pricing / costing / inventory-analysis settings ({@code pricing_policy_id = 1}). */
@Entity
@Table(name = "product_pricing_policy")
public class PricingPolicy {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "pricing_policy_id")
    private Long id;

    @Column(name = "competitive_margin_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal competitiveMarginRate;

    @Column(name = "standard_margin_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal standardMarginRate;

    @Column(name = "premium_margin_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal premiumMarginRate;

    @Column(name = "minimum_margin_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal minimumMarginRate;

    @Column(name = "rounding_mode", nullable = false, length = 30)
    private String roundingMode;

    @Column(name = "margin_basis", nullable = false, length = 20)
    private String marginBasis;

    @Column(name = "tax_type", nullable = false, length = 20)
    private String taxType;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    @Column(name = "costing_method", nullable = false, length = 30)
    private String costingMethod;

    @Column(name = "payment_fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal paymentFeeRate;

    @Column(name = "shipping_cost_per_order", nullable = false)
    private Long shippingCostPerOrder;

    @Column(name = "other_selling_cost_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal otherSellingCostRate;

    @Column(name = "low_stock_cover_days", nullable = false)
    private Integer lowStockCoverDays;

    @Column(name = "safety_stock_days", nullable = false)
    private Integer safetyStockDays;

    @Column(name = "default_lead_days", nullable = false)
    private Integer defaultLeadDays;

    @Column(name = "sales_window_days", nullable = false)
    private Integer salesWindowDays;

    @Column(name = "slow_moving_days", nullable = false)
    private Integer slowMovingDays;

    @Column(name = "slow_moving_max_sales", nullable = false)
    private Integer slowMovingMaxSales;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getId() {
        return id;
    }

    public BigDecimal getCompetitiveMarginRate() {
        return competitiveMarginRate;
    }

    public void setCompetitiveMarginRate(BigDecimal competitiveMarginRate) {
        this.competitiveMarginRate = competitiveMarginRate;
    }

    public BigDecimal getStandardMarginRate() {
        return standardMarginRate;
    }

    public void setStandardMarginRate(BigDecimal standardMarginRate) {
        this.standardMarginRate = standardMarginRate;
    }

    public BigDecimal getPremiumMarginRate() {
        return premiumMarginRate;
    }

    public void setPremiumMarginRate(BigDecimal premiumMarginRate) {
        this.premiumMarginRate = premiumMarginRate;
    }

    public BigDecimal getMinimumMarginRate() {
        return minimumMarginRate;
    }

    public void setMinimumMarginRate(BigDecimal minimumMarginRate) {
        this.minimumMarginRate = minimumMarginRate;
    }

    public String getRoundingMode() {
        return roundingMode;
    }

    public void setRoundingMode(String roundingMode) {
        this.roundingMode = roundingMode;
    }

    public String getMarginBasis() {
        return marginBasis;
    }

    public void setMarginBasis(String marginBasis) {
        this.marginBasis = marginBasis;
    }

    public String getTaxType() {
        return taxType;
    }

    public void setTaxType(String taxType) {
        this.taxType = taxType;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public void setVatRate(BigDecimal vatRate) {
        this.vatRate = vatRate;
    }

    public String getCostingMethod() {
        return costingMethod;
    }

    public void setCostingMethod(String costingMethod) {
        this.costingMethod = costingMethod;
    }

    public BigDecimal getPaymentFeeRate() {
        return paymentFeeRate;
    }

    public void setPaymentFeeRate(BigDecimal paymentFeeRate) {
        this.paymentFeeRate = paymentFeeRate;
    }

    public Long getShippingCostPerOrder() {
        return shippingCostPerOrder;
    }

    public void setShippingCostPerOrder(Long shippingCostPerOrder) {
        this.shippingCostPerOrder = shippingCostPerOrder;
    }

    public BigDecimal getOtherSellingCostRate() {
        return otherSellingCostRate;
    }

    public void setOtherSellingCostRate(BigDecimal otherSellingCostRate) {
        this.otherSellingCostRate = otherSellingCostRate;
    }

    public Integer getLowStockCoverDays() {
        return lowStockCoverDays;
    }

    public void setLowStockCoverDays(Integer lowStockCoverDays) {
        this.lowStockCoverDays = lowStockCoverDays;
    }

    public Integer getSafetyStockDays() {
        return safetyStockDays;
    }

    public void setSafetyStockDays(Integer safetyStockDays) {
        this.safetyStockDays = safetyStockDays;
    }

    public Integer getDefaultLeadDays() {
        return defaultLeadDays;
    }

    public void setDefaultLeadDays(Integer defaultLeadDays) {
        this.defaultLeadDays = defaultLeadDays;
    }

    public Integer getSalesWindowDays() {
        return salesWindowDays;
    }

    public void setSalesWindowDays(Integer salesWindowDays) {
        this.salesWindowDays = salesWindowDays;
    }

    public Integer getSlowMovingDays() {
        return slowMovingDays;
    }

    public void setSlowMovingDays(Integer slowMovingDays) {
        this.slowMovingDays = slowMovingDays;
    }

    public Integer getSlowMovingMaxSales() {
        return slowMovingMaxSales;
    }

    public void setSlowMovingMaxSales(Integer slowMovingMaxSales) {
        this.slowMovingMaxSales = slowMovingMaxSales;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
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
