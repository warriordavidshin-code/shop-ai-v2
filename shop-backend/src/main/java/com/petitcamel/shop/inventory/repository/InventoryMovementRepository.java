package com.petitcamel.shop.inventory.repository;

import com.petitcamel.shop.inventory.domain.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    Page<InventoryMovement> findBySkuIdOrderByCreatedAtDesc(Long skuId, Pageable pageable);

    Page<InventoryMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
