package com.petitcamel.shop.shipping.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Postcode range with a Jeju / remote-island surcharge. */
@Entity
@Table(name = "shipping_extra_area")
public class ShippingExtraArea extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipping_extra_area_id")
    private Long shippingExtraAreaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_type", nullable = false, length = 16)
    private ExtraAreaType areaType;

    @Column(name = "area_name", nullable = false, length = 100)
    private String areaName;

    @Column(name = "postal_code_from", nullable = false, length = 5)
    private String postalCodeFrom;

    @Column(name = "postal_code_to", nullable = false, length = 5)
    private String postalCodeTo;

    /** Area-specific fee; null means the policy fee for {@link #areaType}. */
    @Column(name = "extra_fee")
    private Long extraFee;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ExtraAreaSource source;

    public long effectiveFee(ShippingPolicy policy) {
        return extraFee != null ? extraFee : policy.extraFeeFor(areaType);
    }

    public Long getShippingExtraAreaId() {
        return shippingExtraAreaId;
    }

    public ExtraAreaType getAreaType() {
        return areaType;
    }

    public void setAreaType(ExtraAreaType areaType) {
        this.areaType = areaType;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getPostalCodeFrom() {
        return postalCodeFrom;
    }

    public void setPostalCodeFrom(String postalCodeFrom) {
        this.postalCodeFrom = postalCodeFrom;
    }

    public String getPostalCodeTo() {
        return postalCodeTo;
    }

    public void setPostalCodeTo(String postalCodeTo) {
        this.postalCodeTo = postalCodeTo;
    }

    public Long getExtraFee() {
        return extraFee;
    }

    public void setExtraFee(Long extraFee) {
        this.extraFee = extraFee;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ExtraAreaSource getSource() {
        return source;
    }

    public void setSource(ExtraAreaSource source) {
        this.source = source;
    }
}
