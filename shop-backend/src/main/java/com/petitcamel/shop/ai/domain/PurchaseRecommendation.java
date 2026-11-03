package com.petitcamel.shop.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** Purchase / slow-moving recommendation shown to the admin. Accepting one never places an order. */
@Entity
@Table(name = "ai_recommendation_history")
public class PurchaseRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_recommendation_history_id")
    private Long id;

    @Column(name = "recommendation_type", nullable = false, length = 30)
    private String recommendationType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(nullable = false)
    private int priority;

    @Column(name = "suggested_quantity")
    private Integer suggestedQuantity;

    @Column(name = "metrics_json", nullable = false, columnDefinition = "TEXT")
    private String metricsJson;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "ai_analysis_id")
    private Long aiAnalysisId;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRecommendationType() {
        return recommendationType;
    }

    public void setRecommendationType(String recommendationType) {
        this.recommendationType = recommendationType;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Integer getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public void setSuggestedQuantity(Integer suggestedQuantity) {
        this.suggestedQuantity = suggestedQuantity;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Long getAiAnalysisId() {
        return aiAnalysisId;
    }

    public void setAiAnalysisId(Long aiAnalysisId) {
        this.aiAnalysisId = aiAnalysisId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(Long decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
