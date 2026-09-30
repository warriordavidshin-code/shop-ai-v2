package com.petitcamel.shop.shipping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Immutable history entry. {@code providerStatus} is the vendor's own wording ("배달완료"); {@code status} is the
 * internal status it maps to, or null when it does not map.
 */
@Entity
@Table(name = "shipment_tracking_event")
public class ShipmentTrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tracking_event_id")
    private Long trackingEventId;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private TrackingEventSource source;

    @Column(name = "external_event_id", length = 100)
    private String externalEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    private ShipmentStatus status;

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "raw_hash", length = 64)
    private String rawHash;

    @Column(name = "actor_member_id")
    private Long actorMemberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static ShipmentTrackingEvent internal(
            Long shipmentId, Instant at, String description, ShipmentStatus status, Long actorMemberId) {
        ShipmentTrackingEvent event = new ShipmentTrackingEvent();
        event.shipmentId = shipmentId;
        event.source = TrackingEventSource.INTERNAL;
        event.eventAt = at;
        event.description = description;
        event.status = status;
        event.actorMemberId = actorMemberId;
        return event;
    }

    public static ShipmentTrackingEvent provider(
            Long shipmentId,
            Instant at,
            String externalEventId,
            String providerStatus,
            ShipmentStatus status,
            String location,
            String description,
            String rawHash) {
        ShipmentTrackingEvent event = new ShipmentTrackingEvent();
        event.shipmentId = shipmentId;
        event.source = TrackingEventSource.PROVIDER;
        event.eventAt = at;
        event.externalEventId = externalEventId;
        event.providerStatus = providerStatus;
        event.status = status;
        event.location = location;
        event.description = description;
        event.rawHash = rawHash;
        return event;
    }

    public Long getTrackingEventId() {
        return trackingEventId;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public TrackingEventSource getSource() {
        return source;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public Instant getEventAt() {
        return eventAt;
    }

    public String getRawHash() {
        return rawHash;
    }

    public Long getActorMemberId() {
        return actorMemberId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
