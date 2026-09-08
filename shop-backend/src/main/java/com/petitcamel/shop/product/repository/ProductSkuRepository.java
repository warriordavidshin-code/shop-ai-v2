package com.petitcamel.shop.product.repository;

import com.petitcamel.shop.product.domain.ProductSku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    Optional<ProductSku> findBySkuCode(String skuCode);

    List<ProductSku> findByProductId(Long productId);

    List<ProductSku> findByProductIdIn(Collection<Long> productIds);

    void deleteByProductId(Long productId);
}
