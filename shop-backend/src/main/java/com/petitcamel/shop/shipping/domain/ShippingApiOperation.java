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
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * An external shipping API call. {@code requestId} is our reference sent to the vendor (usable to look the call up
 * again); {@code externalReference} is the vendor's id for the result. {@code responseSummary} is a sanitized JSON
 * summary, never the raw body.
 */
@Entity
@Table(name = "shipping_api_operation")
public class ShippingApiOperation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipping_api_operation_id")
    private Long id;

    @Column(name = "provider_code", nullable = false, length = 30)
    private String providerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30)
    private ShippingOperationType operationType;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Column(name = "idempotency_key", length = 150)
    private String idempotencyKey;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShippingOperationStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    @Column(name = "response_summary", length = 1000)
    private String responseSummary;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Set in the same transaction that applied the vendor result to the shipment / return. */
    @Column(name = "applied_at")
    private Instant appliedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isApplied() {
        return appliedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public ShippingOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(ShippingOperationType operationType) {
        this.operationType = operationType;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public Long getReturnRequestId() {
        return returnRequestId;
    }

    public void setReturnRequestId(Long returnRequestId) {
        this.returnRequestId = returnRequestId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public ShippingOperationStatus getStatus() {
        return status;
    }

    public void setStatus(ShippingOperationStatus status) {
        this.status = status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
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

    public String getResponseSummary() {
        return responseSummary;
    }

    public void setResponseSummary(String responseSummary) {
        this.responseSummary = responseSummary;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public Long getVersion() {
        return version;
    }
}
