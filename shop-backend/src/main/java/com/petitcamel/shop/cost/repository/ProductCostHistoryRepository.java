package com.petitcamel.shop.cost.repository;

import com.petitcamel.shop.cost.domain.ProductCostHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCostHistoryRepository extends JpaRepository<ProductCostHistory, Long> {

    List<ProductCostHistory> findByProductIdOrderByReceivedAtDescIdDesc(Long productId);

    Optional<ProductCostHistory> findFirstByProductIdOrderByReceivedAtDescIdDesc(Long productId);

    Optional<ProductCostHistory> findFirstBySkuIdOrderByReceivedAtDescIdDesc(Long skuId);

    boolean existsByPurchaseReceiptItemId(Long purchaseReceiptItemId);
}
