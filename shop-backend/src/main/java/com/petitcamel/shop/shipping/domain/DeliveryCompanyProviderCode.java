package com.petitcamel.shop.shipping.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The code a vendor uses for a courier (HANJIN via SWEETTRACKER = "05"). Editable without code changes. */
@Entity
@Table(name = "delivery_company_provider_code")
public class DeliveryCompanyProviderCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_company_provider_code_id")
    private Long id;

    @Column(name = "delivery_company_id", nullable = false)
    private Long deliveryCompanyId;

    @Column(name = "shipping_provider_id", nullable = false)
    private Long shippingProviderId;

    @Column(name = "external_company_code", nullable = false, length = 30)
    private String externalCompanyCode;

    public Long getId() {
        return id;
    }

    public Long getDeliveryCompanyId() {
        return deliveryCompanyId;
    }

    public void setDeliveryCompanyId(Long deliveryCompanyId) {
        this.deliveryCompanyId = deliveryCompanyId;
    }

    public Long getShippingProviderId() {
        return shippingProviderId;
    }

    public void setShippingProviderId(Long shippingProviderId) {
        this.shippingProviderId = shippingProviderId;
    }

    public String getExternalCompanyCode() {
        return externalCompanyCode;
    }

    public void setExternalCompanyCode(String externalCompanyCode) {
        this.externalCompanyCode = externalCompanyCode;
    }
}
