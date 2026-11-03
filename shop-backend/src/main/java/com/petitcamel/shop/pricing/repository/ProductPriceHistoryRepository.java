package com.petitcamel.shop.pricing.repository;

import com.petitcamel.shop.pricing.domain.ProductPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, Long> {

    List<ProductPriceHistory> findTop50ByProductIdOrderByChangedAtDesc(Long productId);
}
