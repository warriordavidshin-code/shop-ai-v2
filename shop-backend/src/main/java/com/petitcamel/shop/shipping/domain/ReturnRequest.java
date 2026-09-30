package com.petitcamel.shop.shipping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "return_request")
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 100)
    private ReturnReason reason;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_status", nullable = false, length = 30)
    private ReturnStatus returnStatus;

    @Column(name = "pickup_name", nullable = false, length = 100)
    private String pickupName;

    @Column(name = "pickup_phone", nullable = false, length = 32)
    private String pickupPhone;

    @Column(name = "pickup_postcode", nullable = false, length = 16)
    private String pickupPostcode;

    @Column(name = "pickup_address1", nullable = false, length = 255)
    private String pickupAddress1;

    @Column(name = "pickup_address2", length = 255)
    private String pickupAddress2;

    @Column(name = "pickup_delivery_company", length = 30)
    private String pickupDeliveryCompany;

    @Column(name = "pickup_tracking_number", length = 100)
    private String pickupTrackingNumber;

    @Column(name = "free_return", nullable = false)
    private boolean freeReturn;

    @Column(name = "return_shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal returnShippingFee = BigDecimal.ZERO;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "restocked", nullable = false)
    private boolean restocked;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "pickup_requested_at")
    private Instant pickupRequestedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getReturnRequestId() {
        return returnRequestId;
    }

    public void setReturnRequestId(Long returnRequestId) {
        this.returnRequestId = returnRequestId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public ReturnReason getReason() {
        return reason;
    }

    public void setReason(ReturnReason reason) {
        this.reason = reason;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public ReturnStatus getReturnStatus() {
        return returnStatus;
    }

    public void setReturnStatus(ReturnStatus returnStatus) {
        this.returnStatus = returnStatus;
    }

    public String getPickupName() {
        return pickupName;
    }

    public void setPickupName(String pickupName) {
        this.pickupName = pickupName;
    }

    public String getPickupPhone() {
        return pickupPhone;
    }

    public void setPickupPhone(String pickupPhone) {
        this.pickupPhone = pickupPhone;
    }

    public String getPickupPostcode() {
        return pickupPostcode;
    }

    public void setPickupPostcode(String pickupPostcode) {
        this.pickupPostcode = pickupPostcode;
    }

    public String getPickupAddress1() {
        return pickupAddress1;
    }

    public void setPickupAddress1(String pickupAddress1) {
        this.pickupAddress1 = pickupAddress1;
    }

    public String getPickupAddress2() {
        return pickupAddress2;
    }

    public void setPickupAddress2(String pickupAddress2) {
        this.pickupAddress2 = pickupAddress2;
    }

    public String getPickupDeliveryCompany() {
        return pickupDeliveryCompany;
    }

    public void setPickupDeliveryCompany(String pickupDeliveryCompany) {
        this.pickupDeliveryCompany = pickupDeliveryCompany;
    }

    public String getPickupTrackingNumber() {
        return pickupTrackingNumber;
    }

    public void setPickupTrackingNumber(String pickupTrackingNumber) {
        this.pickupTrackingNumber = pickupTrackingNumber;
    }

    public boolean isFreeReturn() {
        return freeReturn;
    }

    public void setFreeReturn(boolean freeReturn) {
        this.freeReturn = freeReturn;
    }

    public BigDecimal getReturnShippingFee() {
        return returnShippingFee;
    }

    public void setReturnShippingFee(BigDecimal returnShippingFee) {
        this.returnShippingFee = returnShippingFee;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public boolean isRestocked() {
        return restocked;
    }

    public void setRestocked(boolean restocked) {
        this.restocked = restocked;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getAdminMemo() {
        return adminMemo;
    }

    public void setAdminMemo(String adminMemo) {
        this.adminMemo = adminMemo;
    }

    public Long getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(Long processedBy) {
        this.processedBy = processedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getPickupRequestedAt() {
        return pickupRequestedAt;
    }

    public void setPickupRequestedAt(Instant pickupRequestedAt) {
        this.pickupRequestedAt = pickupRequestedAt;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(Instant pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
