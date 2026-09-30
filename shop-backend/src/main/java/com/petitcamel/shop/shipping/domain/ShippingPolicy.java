package com.petitcamel.shop.shipping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Single-row table (policy_id = 1) holding the admin-editable shipping fees. */
@Entity
@Table(name = "shipping_policy")
public class ShippingPolicy {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "policy_id")
    private Integer policyId;

    @Column(name = "base_shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseShippingFee;

    @Column(name = "free_shipping_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal freeShippingAmount;

    @Column(name = "jeju_extra_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal jejuExtraFee;

    @Column(name = "remote_area_extra_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal remoteAreaExtraFee;

    @Column(name = "return_shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal returnShippingFee;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Integer getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Integer policyId) {
        this.policyId = policyId;
    }

    public BigDecimal getBaseShippingFee() {
        return baseShippingFee;
    }

    public void setBaseShippingFee(BigDecimal baseShippingFee) {
        this.baseShippingFee = baseShippingFee;
    }

    public BigDecimal getFreeShippingAmount() {
        return freeShippingAmount;
    }

    public void setFreeShippingAmount(BigDecimal freeShippingAmount) {
        this.freeShippingAmount = freeShippingAmount;
    }

    public BigDecimal getJejuExtraFee() {
        return jejuExtraFee;
    }

    public void setJejuExtraFee(BigDecimal jejuExtraFee) {
        this.jejuExtraFee = jejuExtraFee;
    }

    public BigDecimal getRemoteAreaExtraFee() {
        return remoteAreaExtraFee;
    }

    public void setRemoteAreaExtraFee(BigDecimal remoteAreaExtraFee) {
        this.remoteAreaExtraFee = remoteAreaExtraFee;
    }

    public BigDecimal getReturnShippingFee() {
        return returnShippingFee;
    }

    public void setReturnShippingFee(BigDecimal returnShippingFee) {
        this.returnShippingFee = returnShippingFee;
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
}
