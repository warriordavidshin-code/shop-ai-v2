package com.petitcamel.shop.pricing.repository;

import com.petitcamel.shop.pricing.domain.PricingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, Long> {
}
