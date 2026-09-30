package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShippingProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShippingProviderRepository extends JpaRepository<ShippingProvider, Long> {

    Optional<ShippingProvider> findByCode(String code);

    List<ShippingProvider> findAllByOrderBySortOrderAscShippingProviderIdAsc();
}
