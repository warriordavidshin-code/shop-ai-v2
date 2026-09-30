package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.DeliveryCompanyProviderCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryCompanyProviderCodeRepository extends JpaRepository<DeliveryCompanyProviderCode, Long> {

    Optional<DeliveryCompanyProviderCode> findByDeliveryCompanyIdAndShippingProviderId(
            Long deliveryCompanyId, Long shippingProviderId);
}
