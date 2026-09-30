package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShippingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingPolicyRepository extends JpaRepository<ShippingPolicy, Integer> {
}
