package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShippingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShippingPolicyRepository extends JpaRepository<ShippingPolicy, Long> {

    Optional<ShippingPolicy> findFirstByEnabledTrueOrderByShippingPolicyIdAsc();
}
