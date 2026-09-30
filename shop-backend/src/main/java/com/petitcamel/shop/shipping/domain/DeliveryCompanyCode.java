package com.petitcamel.shop.shipping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** Maps an internal courier code to the code a specific tracking provider expects. */
@Entity
@Table(name = "delivery_company_code")
@IdClass(DeliveryCompanyCode.Key.class)
public class DeliveryCompanyCode {

    @Id
    @Column(name = "company_code", length = 30)
    private String companyCode;

    @Id
    @Column(name = "provider", length = 30)
    private String provider;

    @Column(name = "provider_code", nullable = false, length = 30)
    private String providerCode;

    public String getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public static class Key implements Serializable {
        private String companyCode;
        private String provider;

        public Key() {
        }

        public Key(String companyCode, String provider) {
            this.companyCode = companyCode;
            this.provider = provider;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(companyCode, key.companyCode) && Objects.equals(provider, key.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hash(companyCode, provider);
        }
    }
}
