package com.petitcamel.shop.cost.repository;

import com.petitcamel.shop.cost.domain.SkuCost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SkuCostRepository extends JpaRepository<SkuCost, Long> {

    List<SkuCost> findBySkuIdIn(Collection<Long> skuIds);
}
