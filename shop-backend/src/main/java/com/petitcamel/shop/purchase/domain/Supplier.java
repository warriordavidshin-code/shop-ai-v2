package com.petitcamel.shop.purchase.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "supplier")
public class Supplier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Long id;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "business_no", length = 20)
    private String businessNo;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "address", length = 300)
    private String address;

    /** Admin-entered lead time; when null the average from confirmed receipts is used. */
    @Column(name = "lead_days")
    private Integer leadDays;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public Long getId() {
        return id;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public void setBusinessNo(String businessNo) {
        this.businessNo = businessNo;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getLeadDays() {
        return leadDays;
    }

    public void setLeadDays(Integer leadDays) {
        this.leadDays = leadDays;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
