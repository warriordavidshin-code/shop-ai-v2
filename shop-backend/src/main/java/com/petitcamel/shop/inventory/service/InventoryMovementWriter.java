package com.petitcamel.shop.inventory.service;

import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.domain.InventoryMovement;
import com.petitcamel.shop.inventory.domain.MovementType;
import com.petitcamel.shop.inventory.repository.InventoryMovementRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single place that appends inventory history rows, including product id and on-hand stock before / after,
 * so every stock change (orders, cancels, returns, adjustments, receiving) is traceable.
 */
@Component
public class InventoryMovementWriter {

    private final InventoryMovementRepository repository;
    private final ProductSkuRepository productSkuRepository;

    public InventoryMovementWriter(InventoryMovementRepository repository, ProductSkuRepository productSkuRepository) {
        this.repository = repository;
        this.productSkuRepository = productSkuRepository;
    }

    public record Entry(
            Long skuId,
            MovementType type,
            int quantity,
            String referenceType,
            String referenceId,
            String reason,
            Long actorMemberId,
            Instant at,
            Integer stockBefore,
            Integer stockAfter,
            Long purchaseReceiptId,
            BigDecimal unitCost
    ) {
    }

    public InventoryMovement write(Entry entry) {
        InventoryMovement movement = new InventoryMovement();
        movement.setSkuId(entry.skuId());
        movement.setProductId(productSkuRepository.findById(entry.skuId())
                .map(sku -> sku.getProductId()).orElse(null));
        movement.setMovementType(entry.type());
        movement.setQuantity(entry.quantity());
        movement.setReferenceType(entry.referenceType());
        movement.setReferenceId(entry.referenceId());
        movement.setReason(entry.reason());
        movement.setActorMemberId(entry.actorMemberId());
        movement.setCreatedAt(entry.at());
        movement.setStockBefore(entry.stockBefore());
        movement.setStockAfter(entry.stockAfter());
        movement.setPurchaseReceiptId(entry.purchaseReceiptId());
        movement.setUnitCost(entry.unitCost());
        return repository.save(movement);
    }

    /** Convenience for changes where the caller holds the inventory row after the change. */
    public InventoryMovement write(Inventory inventory, int stockBefore, MovementType type, int quantity,
                                   String referenceType, String referenceId, String reason, Long actorMemberId,
                                   Instant at) {
        return write(new Entry(inventory.getSkuId(), type, quantity, referenceType, referenceId, reason,
                actorMemberId, at, stockBefore, inventory.getStockQuantity(), null, null));
    }
}
