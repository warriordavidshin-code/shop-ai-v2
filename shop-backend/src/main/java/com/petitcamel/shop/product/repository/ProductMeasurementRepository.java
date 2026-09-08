package com.petitcamel.shop.product.repository;

import com.petitcamel.shop.product.domain.ProductMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductMeasurementRepository extends JpaRepository<ProductMeasurement, Long> {

    List<ProductMeasurement> findByProductIdOrderBySizeAsc(Long productId);
}
