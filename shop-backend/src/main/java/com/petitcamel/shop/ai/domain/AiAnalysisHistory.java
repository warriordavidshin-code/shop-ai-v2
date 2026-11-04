package com.petitcamel.shop.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One AI call (or mock run); successful rows double as the result cache keyed by {@code inputHash}. */
@Entity
@Table(name = "ai_analysis_history")
public class AiAnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_analysis_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 30)
    private AiAnalysisType analysisType;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "input_file_count", nullable = false)
    private int inputFileCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AiProviderType provider;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 20)
    private AiAnalysisStatus status;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "estimated_cost_usd", precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "billing_type", nullable = false, length = 10)
    private String billingType = "PAID";

    public Long getId() {
        return id;
    }

    public String getBillingType() {
        return billingType;
    }

    public void setBillingType(String billingType) {
        this.billingType = billingType;
    }

    public AiAnalysisType getAnalysisType() {
        return analysisType;
    }

    public void setAnalysisType(AiAnalysisType analysisType) {
        this.analysisType = analysisType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String inputHash) {
        this.inputHash = inputHash;
    }

    public int getInputFileCount() {
        return inputFileCount;
    }

    public void setInputFileCount(int inputFileCount) {
        this.inputFileCount = inputFileCount;
    }

    public AiProviderType getProvider() {
        return provider;
    }

    public void setProvider(AiProviderType provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public AiAnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AiAnalysisStatus status) {
        this.status = status;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
