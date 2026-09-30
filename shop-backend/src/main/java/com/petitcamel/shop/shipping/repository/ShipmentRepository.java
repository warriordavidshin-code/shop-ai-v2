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

    /** Latest shipment of a type (an order has one DELIVERY but may have several RETURN legs over time). */
    Optional<Shipment> findFirstByOrderIdAndShipmentTypeOrderByShipmentIdDesc(Long orderId, ShipmentType shipmentType);

    Optional<Shipment> findByReturnRequestId(Long returnRequestId);

    List<Shipment> findByReturnRequestIdIn(Collection<Long> returnRequestIds);

    List<Shipment> findByOrderIdInAndShipmentType(Collection<Long> orderIds, ShipmentType shipmentType);

    Optional<Shipment> findFirstByDeliveryCompanyIdAndTrackingNumberAndStatusNot(
            Long deliveryCompanyId, String trackingNumber, ShipmentStatus status);

    long countByShipmentTypeAndStatusIn(ShipmentType shipmentType, Collection<ShipmentStatus> statuses);

    /** Shipments due for a tracking refresh: unsettled, with an invoice, not checked since {@code checkedBefore}. */
    @Query("""
            SELECT s.shipmentId FROM Shipment s
            WHERE s.status IN :statuses
              AND s.trackingNumber IS NOT NULL
              AND (s.lastTrackingCheckedAt IS NULL OR s.lastTrackingCheckedAt < :checkedBefore)
            ORDER BY s.lastTrackingCheckedAt ASC NULLS FIRST, s.shipmentId ASC
            """)
    List<Long> findDueForTracking(
            @Param("statuses") Collection<ShipmentStatus> statuses,
            @Param("checkedBefore") Instant checkedBefore,
            Pageable pageable);
}
