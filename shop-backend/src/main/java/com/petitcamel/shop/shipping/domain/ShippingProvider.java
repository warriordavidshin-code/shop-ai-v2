package com.petitcamel.shop.shipping.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An external shipping API vendor (SWEETTRACKER, GOODSFLOW) or MANUAL. Holds operational switches only; API keys
 * and secrets come from environment variables.
 */
@Entity
@Table(name = "shipping_provider")
public class ShippingProvider extends BaseEntity {

    public static final String MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipping_provider_id")
    private Long shippingProviderId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "tracking_enabled", nullable = false)
    private boolean trackingEnabled;

    @Column(name = "waybill_enabled", nullable = false)
    private boolean waybillEnabled;

    @Column(name = "pickup_enabled", nullable = false)
    private boolean pickupEnabled;

    @Column(name = "return_pickup_enabled", nullable = false)
    private boolean returnPickupEnabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Operator switch for a capability (independent of whether the Java client implements it). */
    public boolean allows(ProviderCapability capability) {
        if (!enabled) {
            return false;
        }
        return switch (capability) {
            case TRACKING -> trackingEnabled;
            case WAYBILL -> waybillEnabled;
            case PICKUP -> pickupEnabled;
            case RETURN_PICKUP -> returnPickupEnabled;
        };
    }

    public boolean isManual() {
        return MANUAL.equals(code);
    }

    public Long getShippingProviderId() {
        return shippingProviderId;
    }

    public void setShippingProviderId(Long shippingProviderId) {
        this.shippingProviderId = shippingProviderId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrackingEnabled() {
        return trackingEnabled;
    }

    public void setTrackingEnabled(boolean trackingEnabled) {
        this.trackingEnabled = trackingEnabled;
    }

    public boolean isWaybillEnabled() {
        return waybillEnabled;
    }

    public void setWaybillEnabled(boolean waybillEnabled) {
        this.waybillEnabled = waybillEnabled;
    }

    public boolean isPickupEnabled() {
        return pickupEnabled;
    }

    public void setPickupEnabled(boolean pickupEnabled) {
        this.pickupEnabled = pickupEnabled;
    }

    public boolean isReturnPickupEnabled() {
        return returnPickupEnabled;
    }

    public void setReturnPickupEnabled(boolean returnPickupEnabled) {
        this.returnPickupEnabled = returnPickupEnabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
