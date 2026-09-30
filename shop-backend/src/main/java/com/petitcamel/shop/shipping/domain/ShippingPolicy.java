package com.petitcamel.shop.shipping.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Shipping fees in whole won. Several policies may be stored; exactly one is enabled. */
@Entity
@Table(name = "shipping_policy")
public class ShippingPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipping_policy_id")
    private Long shippingPolicyId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "base_shipping_fee", nullable = false)
    private long baseShippingFee;

    @Column(name = "free_shipping_threshold", nullable = false)
    private long freeShippingThreshold;

    @Column(name = "jeju_extra_fee", nullable = false)
    private long jejuExtraFee;

    @Column(name = "remote_area_extra_fee", nullable = false)
    private long remoteAreaExtraFee;

    @Column(name = "return_shipping_fee", nullable = false)
    private long returnShippingFee;

    @Column(name = "exchange_shipping_fee", nullable = false)
    private long exchangeShippingFee;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public long extraFeeFor(ExtraAreaType areaType) {
        if (areaType == null) {
            return 0;
        }
        return areaType == ExtraAreaType.JEJU ? jejuExtraFee : remoteAreaExtraFee;
    }

    public Long getShippingPolicyId() {
        return shippingPolicyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getBaseShippingFee() {
        return baseShippingFee;
    }

    public void setBaseShippingFee(long baseShippingFee) {
        this.baseShippingFee = baseShippingFee;
    }

    public long getFreeShippingThreshold() {
        return freeShippingThreshold;
    }

    public void setFreeShippingThreshold(long freeShippingThreshold) {
        this.freeShippingThreshold = freeShippingThreshold;
    }

    public long getJejuExtraFee() {
        return jejuExtraFee;
    }

    public void setJejuExtraFee(long jejuExtraFee) {
        this.jejuExtraFee = jejuExtraFee;
    }

    public long getRemoteAreaExtraFee() {
        return remoteAreaExtraFee;
    }

    public void setRemoteAreaExtraFee(long remoteAreaExtraFee) {
        this.remoteAreaExtraFee = remoteAreaExtraFee;
    }

    public long getReturnShippingFee() {
        return returnShippingFee;
    }

    public void setReturnShippingFee(long returnShippingFee) {
        this.returnShippingFee = returnShippingFee;
    }

    public long getExchangeShippingFee() {
        return exchangeShippingFee;
    }

    public void setExchangeShippingFee(long exchangeShippingFee) {
        this.exchangeShippingFee = exchangeShippingFee;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
