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

import java.time.Instant;

@Entity
@Table(name = "shipment")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_type", nullable = false, length = 16)
    private ShipmentType shipmentType;

    @Column(name = "delivery_company", length = 30)
    private String deliveryCompany;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_status", nullable = false, length = 32)
    private ShipmentStatus shipmentStatus;

    @Column(name = "pickup_requested_at")
    private Instant pickupRequestedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_tracking_checked_at")
    private Instant lastTrackingCheckedAt;

    @Column(name = "last_tracking_error", length = 300)
    private String lastTrackingError;

    @Column(name = "tracking_fail_count", nullable = false)
    private int trackingFailCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean hasTrackingNumber() {
        return trackingNumber != null && !trackingNumber.isBlank();
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getReturnRequestId() {
        return returnRequestId;
    }

    public void setReturnRequestId(Long returnRequestId) {
        this.returnRequestId = returnRequestId;
    }

    public ShipmentType getShipmentType() {
        return shipmentType;
    }

    public void setShipmentType(ShipmentType shipmentType) {
        this.shipmentType = shipmentType;
    }

    public String getDeliveryCompany() {
        return deliveryCompany;
    }

    public void setDeliveryCompany(String deliveryCompany) {
        this.deliveryCompany = deliveryCompany;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public ShipmentStatus getShipmentStatus() {
        return shipmentStatus;
    }

    public void setShipmentStatus(ShipmentStatus shipmentStatus) {
        this.shipmentStatus = shipmentStatus;
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

    public Instant getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(Instant shippedAt) {
        this.shippedAt = shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getLastTrackingCheckedAt() {
        return lastTrackingCheckedAt;
    }

    public void setLastTrackingCheckedAt(Instant lastTrackingCheckedAt) {
        this.lastTrackingCheckedAt = lastTrackingCheckedAt;
    }

    public String getLastTrackingError() {
        return lastTrackingError;
    }

    public void setLastTrackingError(String lastTrackingError) {
        this.lastTrackingError = lastTrackingError;
    }

    public int getTrackingFailCount() {
        return trackingFailCount;
    }

    public void setTrackingFailCount(int trackingFailCount) {
        this.trackingFailCount = trackingFailCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
