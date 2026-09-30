package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShipmentTrackingEvent;
import com.petitcamel.shop.shipping.domain.TrackingEventSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShipmentTrackingEventRepository extends JpaRepository<ShipmentTrackingEvent, Long> {

    List<ShipmentTrackingEvent> findByShipmentIdOrderByEventTimeAscEventIdAsc(Long shipmentId);

    @Modifying
    @Query("DELETE FROM ShipmentTrackingEvent e WHERE e.shipmentId = :shipmentId AND e.source = :source")
    int deleteByShipmentIdAndSource(@Param("shipmentId") Long shipmentId, @Param("source") TrackingEventSource source);
}
