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
 * One physical movement of goods: a delivery, a return pickup, or (later) an exchange leg. Related rows are
 * referenced by id only; screens load them in batches instead of through JPA associations.
 */
@Entity
@Table(name = "shipment")
public class Shipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_type", nullable = false, length = 20)
    private ShipmentType shipmentType;

    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Column(name = "delivery_company_id")
    private Long deliveryCompanyId;

    /** Vendor that last issued / picked up / tracked this parcel; null while handled by hand. */
    @Column(name = "shipping_provider_id")
    private Long shippingProviderId;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ShipmentStatus status;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    @Column(name = "address1", length = 255)
    private String address1;

    @Column(name = "address2", length = 255)
    private String address2;

    @Column(name = "pickup_requested_at")
    private Instant pickupRequestedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "out_for_delivery_at")
    private Instant outForDeliveryAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_tracking_checked_at")
    private Instant lastTrackingCheckedAt;

    @Column(name = "last_tracking_error", length = 300)
    private String lastTrackingError;

    @Column(name = "tracking_fail_count", nullable = false)
    private int trackingFailCount;

    @Column(name = "last_status_changed_at")
    private Instant lastStatusChangedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean hasTrackingNumber() {
        return trackingNumber != null && !trackingNumber.isBlank();
    }

    /** Points the shipment at a new invoice and forgets tracking state of the previous one. */
    public void assignInvoice(Long deliveryCompanyId, String trackingNumber) {
        this.deliveryCompanyId = deliveryCompanyId;
        this.trackingNumber = trackingNumber;
        this.lastTrackingCheckedAt = null;
        this.lastTrackingError = null;
        this.trackingFailCount = 0;
    }

    /** Sets the status and fills the milestone timestamp for it (first occurrence wins). */
    public void changeStatus(ShipmentStatus to, Instant at) {
        this.status = to;
        this.lastStatusChangedAt = at;
        if (to == ShipmentStatus.PICKUP_REQUESTED && pickupRequestedAt == null) {
            pickupRequestedAt = at;
        }
        if (to.isMoving()) {
            if (pickedUpAt == null) {
                pickedUpAt = at;
            }
            if (shippedAt == null) {
                shippedAt = at;
            }
        }
        if (to == ShipmentStatus.OUT_FOR_DELIVERY && outForDeliveryAt == null) {
            outForDeliveryAt = at;
        }
        if (to == ShipmentStatus.DELIVERED) {
            deliveredAt = at;
        }
    }

    public void setContact(String name, String phone, String postalCode, String address1, String address2) {
        this.contactName = name;
        this.contactPhone = phone;
        this.postalCode = postalCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public ShipmentType getShipmentType() {
        return shipmentType;
    }

    public void setShipmentType(ShipmentType shipmentType) {
        this.shipmentType = shipmentType;
    }

    public Long getReturnRequestId() {
        return returnRequestId;
    }

    public void setReturnRequestId(Long returnRequestId) {
        this.returnRequestId = returnRequestId;
    }

    public Long getDeliveryCompanyId() {
        return deliveryCompanyId;
    }

    public Long getShippingProviderId() {
        return shippingProviderId;
    }

    public void setShippingProviderId(Long shippingProviderId) {
        this.shippingProviderId = shippingProviderId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    /** Initial status only; later changes go through {@link #changeStatus}. */
    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public Instant getPickupRequestedAt() {
        return pickupRequestedAt;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getOutForDeliveryAt() {
        return outForDeliveryAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
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

    public Instant getLastStatusChangedAt() {
        return lastStatusChangedAt;
    }

    public Long getVersion() {
        return version;
    }
}
