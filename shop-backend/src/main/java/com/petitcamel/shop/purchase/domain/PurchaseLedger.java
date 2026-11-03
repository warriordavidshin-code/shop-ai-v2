package com.petitcamel.shop.purchase.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EntrySource;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceType;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.KeptStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.PaymentMethod;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.VerificationStatus;
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
import java.util.ArrayList;
import java.util.List;

/** One row of the 매입장부 sheet. Soft-deleted via {@code deletedAt}. */
@Entity
@Table(name = "purchase_ledger")
public class PurchaseLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_ledger_id")
    private Long id;

    @Column(name = "management_no", nullable = false, length = 20)
    private String managementNo;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "supplier_name", length = 200)
    private String supplierName;

    @Column(name = "supplier_business_no", length = 20)
    private String supplierBusinessNo;

    @Column(name = "item_summary", length = 4000)
    private String itemSummary;

    @Column(name = "purchase_amount")
    private Long purchaseAmount;

    @Column(name = "supply_amount")
    private Long supplyAmount;

    @Column(name = "tax_amount")
    private Long taxAmount;

    @Column(name = "payment_amount")
    private Long paymentAmount;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "transfer_reference", length = 100)
    private String transferReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_kept", nullable = false, length = 20)
    private KeptStatus statementKept = KeptStatus.NOT_KEPT;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_kept", nullable = false, length = 20)
    private KeptStatus transferKept = KeptStatus.NOT_KEPT;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 20)
    private EvidenceType evidenceType = EvidenceType.NONE;

    @Column(name = "evidence_location", length = 500)
    private String evidenceLocation;

    @Column(name = "remarks", length = 4000)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.NEEDS_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_source", nullable = false, length = 20)
    private EntrySource entrySource = EntrySource.MANUAL;

    @Column(name = "source_analysis_id")
    private Long sourceAnalysisId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 차액 = 매입액 - 지급액, blank when 매입액 is blank (sheet formula IF(F="","",F-G)). */
    public Long differenceAmount() {
        if (purchaseAmount == null) {
            return null;
        }
        return purchaseAmount - (paymentAmount == null ? 0 : paymentAmount);
    }

    /**
     * Fields the sheet's 자료 확인 formula requires: B,C,D,F,G,H,I filled and L = M = 보관.
     * Returns the Korean column names that are missing.
     */
    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (purchaseDate == null) {
            missing.add("매입일");
        }
        if (isBlank(supplierName)) {
            missing.add("거래처 상호");
        }
        if (isBlank(supplierBusinessNo)) {
            missing.add("사업자번호");
        }
        if (purchaseAmount == null) {
            missing.add("매입액");
        }
        if (paymentAmount == null) {
            missing.add("지급액");
        }
        if (paymentDate == null) {
            missing.add("지급일");
        }
        if (isBlank(recipientName)) {
            missing.add("수취인·예금주");
        }
        if (statementKept != KeptStatus.KEPT) {
            missing.add("명세표 보관");
        }
        if (transferKept != KeptStatus.KEPT) {
            missing.add("이체내역 보관");
        }
        return missing;
    }

    public void refreshVerificationStatus() {
        verificationStatus = missingFields().isEmpty()
                ? VerificationStatus.BASIC_COMPLETE
                : VerificationStatus.NEEDS_REVIEW;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public Long getId() {
        return id;
    }

    public String getManagementNo() {
        return managementNo;
    }

    public void setManagementNo(String managementNo) {
        this.managementNo = managementNo;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getSupplierBusinessNo() {
        return supplierBusinessNo;
    }

    public void setSupplierBusinessNo(String supplierBusinessNo) {
        this.supplierBusinessNo = supplierBusinessNo;
    }

    public String getItemSummary() {
        return itemSummary;
    }

    public void setItemSummary(String itemSummary) {
        this.itemSummary = itemSummary;
    }

    public Long getPurchaseAmount() {
        return purchaseAmount;
    }

    public void setPurchaseAmount(Long purchaseAmount) {
        this.purchaseAmount = purchaseAmount;
    }

    public Long getSupplyAmount() {
        return supplyAmount;
    }

    public void setSupplyAmount(Long supplyAmount) {
        this.supplyAmount = supplyAmount;
    }

    public Long getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(Long taxAmount) {
        this.taxAmount = taxAmount;
    }

    public Long getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(Long paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getTransferReference() {
        return transferReference;
    }

    public void setTransferReference(String transferReference) {
        this.transferReference = transferReference;
    }

    public KeptStatus getStatementKept() {
        return statementKept;
    }

    public void setStatementKept(KeptStatus statementKept) {
        this.statementKept = statementKept;
    }

    public KeptStatus getTransferKept() {
        return transferKept;
    }

    public void setTransferKept(KeptStatus transferKept) {
        this.transferKept = transferKept;
    }

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(EvidenceType evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getEvidenceLocation() {
        return evidenceLocation;
    }

    public void setEvidenceLocation(String evidenceLocation) {
        this.evidenceLocation = evidenceLocation;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public EntrySource getEntrySource() {
        return entrySource;
    }

    public void setEntrySource(EntrySource entrySource) {
        this.entrySource = entrySource;
    }

    public Long getSourceAnalysisId() {
        return sourceAnalysisId;
    }

    public void setSourceAnalysisId(Long sourceAnalysisId) {
        this.sourceAnalysisId = sourceAnalysisId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(Long deletedBy) {
        this.deletedBy = deletedBy;
    }

    public Long getVersion() {
        return version;
    }
}
