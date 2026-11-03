package com.petitcamel.shop.purchase.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.AllocationMethod;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/** 입고 건. Stock changes only when the receipt is confirmed. */
@Entity
@Table(name = "purchase_receipt")
public class PurchaseReceipt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_receipt_id")
    private Long id;

    @Column(name = "receipt_no", nullable = false, length = 30)
    private String receiptNo;

    @Column(name = "purchase_ledger_id")
    private Long purchaseLedgerId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", nullable = false, length = 20)
    private ReceiptStatus status = ReceiptStatus.PENDING;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "freight_cost", nullable = false)
    private Long freightCost = 0L;

    @Column(name = "incidental_cost", nullable = false)
    private Long incidentalCost = 0L;

    @Column(name = "packaging_cost", nullable = false)
    private Long packagingCost = 0L;

    @Column(name = "other_cost", nullable = false)
    private Long otherCost = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_method", nullable = false, length = 20)
    private AllocationMethod allocationMethod = AllocationMethod.AMOUNT;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public long additionalCostTotal() {
        return nz(freightCost) + nz(incidentalCost) + nz(packagingCost) + nz(otherCost);
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    public Long getId() {
        return id;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public Long getPurchaseLedgerId() {
        return purchaseLedgerId;
    }

    public void setPurchaseLedgerId(Long purchaseLedgerId) {
        this.purchaseLedgerId = purchaseLedgerId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public void setStatus(ReceiptStatus status) {
        this.status = status;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public Long getFreightCost() {
        return freightCost;
    }

    public void setFreightCost(Long freightCost) {
        this.freightCost = freightCost;
    }

    public Long getIncidentalCost() {
        return incidentalCost;
    }

    public void setIncidentalCost(Long incidentalCost) {
        this.incidentalCost = incidentalCost;
    }

    public Long getPackagingCost() {
        return packagingCost;
    }

    public void setPackagingCost(Long packagingCost) {
        this.packagingCost = packagingCost;
    }

    public Long getOtherCost() {
        return otherCost;
    }

    public void setOtherCost(Long otherCost) {
        this.otherCost = otherCost;
    }

    public AllocationMethod getAllocationMethod() {
        return allocationMethod;
    }

    public void setAllocationMethod(AllocationMethod allocationMethod) {
        this.allocationMethod = allocationMethod;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Long getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(Long confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(Long cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getVersion() {
        return version;
    }
}
