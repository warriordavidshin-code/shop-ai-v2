package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.Shipment;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderIdAndShipmentType(Long orderId, ShipmentType shipmentType);

    Optional<Shipment> findByReturnRequestId(Long returnRequestId);

    List<Shipment> findByOrderIdInAndShipmentType(Collection<Long> orderIds, ShipmentType shipmentType);

    /** Shipments due for a tracking refresh: in transit, with an invoice, not checked since {@code checkedBefore}. */
    @Query("""
            SELECT s.shipmentId FROM Shipment s
            WHERE s.shipmentStatus IN :statuses
              AND s.trackingNumber IS NOT NULL
              AND (s.lastTrackingCheckedAt IS NULL OR s.lastTrackingCheckedAt < :checkedBefore)
            ORDER BY s.lastTrackingCheckedAt ASC NULLS FIRST, s.shipmentId ASC
            """)
    List<Long> findDueForTracking(
            @Param("statuses") Collection<ShipmentStatus> statuses,
            @Param("checkedBefore") Instant checkedBefore,
            Pageable pageable);
}
