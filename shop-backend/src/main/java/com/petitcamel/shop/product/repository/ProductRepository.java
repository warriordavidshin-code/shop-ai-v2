package com.petitcamel.shop.product.repository;

import com.petitcamel.shop.product.domain.Product;
import com.petitcamel.shop.product.domain.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByStatus(ProductStatus status);

    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status, Pageable pageable);

    List<Product> findByStatusOrderByProductIdAsc(ProductStatus status, Pageable pageable);

    long countByStatus(ProductStatus status);
}
