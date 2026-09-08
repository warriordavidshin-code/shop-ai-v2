package com.petitcamel.shop.inventory.repository;

import com.petitcamel.shop.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySkuId(Long skuId);

    List<Inventory> findBySkuIdIn(Collection<Long> skuIds);

    @Query("""
            SELECT COUNT(i) FROM Inventory i
            WHERE (i.stockQuantity - i.reservedQuantity) <= i.reorderPoint
            """)
    long countLowStock();
}
