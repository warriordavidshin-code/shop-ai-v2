package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShippingNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingNotificationRepository extends JpaRepository<ShippingNotification, Long> {

    boolean existsByShipmentIdAndEventType(Long shipmentId, String eventType);
}
