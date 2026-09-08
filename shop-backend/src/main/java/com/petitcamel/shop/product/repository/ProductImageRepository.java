package com.petitcamel.shop.product.repository;

import com.petitcamel.shop.product.domain.ImageType;
import com.petitcamel.shop.product.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    List<ProductImage> findByProductIdIn(Collection<Long> productIds);

    Optional<ProductImage> findFirstByProductIdAndImageTypeOrderBySortOrderAsc(Long productId, ImageType imageType);

    void deleteByProductId(Long productId);
}
