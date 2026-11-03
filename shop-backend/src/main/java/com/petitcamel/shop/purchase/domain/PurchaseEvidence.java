package com.petitcamel.shop.purchase.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceAiStatus;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evidence file stored in the private upload directory; served only through the admin API. */
@Entity
@Table(name = "purchase_evidence")
public class PurchaseEvidence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_evidence_id")
    private Long id;

    @Column(name = "purchase_ledger_id")
    private Long purchaseLedgerId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /** Path relative to the private upload root. */
    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_category", nullable = false, length = 30)
    private EvidenceCategory evidenceCategory = EvidenceCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_analysis_status", nullable = false, length = 20)
    private EvidenceAiStatus aiAnalysisStatus = EvidenceAiStatus.NOT_ANALYZED;

    @Column(name = "ai_analysis_id")
    private Long aiAnalysisId;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    public Long getId() {
        return id;
    }

    public Long getPurchaseLedgerId() {
        return purchaseLedgerId;
    }

    public void setPurchaseLedgerId(Long purchaseLedgerId) {
        this.purchaseLedgerId = purchaseLedgerId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public EvidenceCategory getEvidenceCategory() {
        return evidenceCategory;
    }

    public void setEvidenceCategory(EvidenceCategory evidenceCategory) {
        this.evidenceCategory = evidenceCategory;
    }

    public EvidenceAiStatus getAiAnalysisStatus() {
        return aiAnalysisStatus;
    }

    public void setAiAnalysisStatus(EvidenceAiStatus aiAnalysisStatus) {
        this.aiAnalysisStatus = aiAnalysisStatus;
    }

    public Long getAiAnalysisId() {
        return aiAnalysisId;
    }

    public void setAiAnalysisId(Long aiAnalysisId) {
        this.aiAnalysisId = aiAnalysisId;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
