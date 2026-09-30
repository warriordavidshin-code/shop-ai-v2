package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShipmentTrackingEvent;
import com.petitcamel.shop.shipping.domain.TrackingEventSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShipmentTrackingEventRepository extends JpaRepository<ShipmentTrackingEvent, Long> {

    List<ShipmentTrackingEvent> findByShipmentIdOrderByEventAtAscTrackingEventIdAsc(Long shipmentId);

    @Query("SELECT e.rawHash FROM ShipmentTrackingEvent e WHERE e.shipmentId = :shipmentId AND e.rawHash IS NOT NULL")
    List<String> findRawHashes(@Param("shipmentId") Long shipmentId);

    @Query("""
            SELECT e.externalEventId FROM ShipmentTrackingEvent e
            WHERE e.shipmentId = :shipmentId AND e.externalEventId IS NOT NULL
            """)
    List<String> findExternalEventIds(@Param("shipmentId") Long shipmentId);

    /** Only used when an invoice number is corrected: the old invoice's courier events no longer apply. */
    @Modifying
    @Query("DELETE FROM ShipmentTrackingEvent e WHERE e.shipmentId = :shipmentId AND e.source = :source")
    int deleteByShipmentIdAndSource(@Param("shipmentId") Long shipmentId, @Param("source") TrackingEventSource source);
}
