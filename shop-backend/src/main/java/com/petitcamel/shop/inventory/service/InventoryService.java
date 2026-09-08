package com.petitcamel.shop.inventory.service;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.domain.InventoryMovement;
import com.petitcamel.shop.inventory.domain.MovementType;
import com.petitcamel.shop.inventory.dto.InventoryAdjustRequest;
import com.petitcamel.shop.inventory.dto.InventoryMovementResponse;
import com.petitcamel.shop.inventory.dto.InventoryResponse;
import com.petitcamel.shop.inventory.repository.InventoryMovementRepository;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.product.repository.ProductSkuRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final ProductSkuRepository productSkuRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryMovementRepository inventoryMovementRepository,
            ProductSkuRepository productSkuRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.productSkuRepository = productSkuRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public InventoryResponse adjust(Long skuId, InventoryAdjustRequest request, Long actorMemberId) {
        if (!productSkuRepository.existsById(skuId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU를 찾을 수 없습니다.");
        }

        Inventory inventory = inventoryRepository.findBySkuId(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));

        try {
            inventory.applyAdjustment(request.quantityDelta());
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getMessage());
        }

        Instant now = clock.instant();
        inventory.setUpdatedAt(now);

        try {
            inventoryRepository.saveAndFlush(inventory);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "재고가 다른 요청에 의해 변경되었습니다. 다시 시도해 주세요.");
        }

        InventoryMovement movement = new InventoryMovement();
        movement.setSkuId(skuId);
        movement.setMovementType(MovementType.ADJUSTMENT);
        movement.setQuantity(request.quantityDelta());
        movement.setReason(request.reason());
        movement.setActorMemberId(actorMemberId);
        movement.setCreatedAt(now);
        inventoryMovementRepository.save(movement);

        String reasonBrief = request.reason() == null ? "" : request.reason().trim();
        if (reasonBrief.length() > 120) {
            reasonBrief = reasonBrief.substring(0, 120);
        }
        auditLogService.record(
                actorMemberId,
                "INVENTORY_ADJUST",
                "SKU",
                String.valueOf(skuId),
                "quantityDelta=" + request.quantityDelta()
                        + (reasonBrief.isEmpty() ? "" : ", reason=" + reasonBrief));

        log.info("Inventory adjusted skuId={} delta={} stock={} reserved={} actorMemberId={}",
                skuId,
                request.quantityDelta(),
                inventory.getStockQuantity(),
                inventory.getReservedQuantity(),
                actorMemberId);

        return toResponse(inventory);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> listMovements(Long skuId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize);

        Page<InventoryMovement> movements = skuId == null
                ? inventoryMovementRepository.findAllByOrderByCreatedAtDesc(pageable)
                : inventoryMovementRepository.findBySkuIdOrderByCreatedAtDesc(skuId, pageable);

        return PageResponse.of(
                movements.getContent().stream().map(this::toMovementResponse).toList(),
                movements.getNumber(),
                movements.getSize(),
                movements.getTotalElements());
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getInventoryId(),
                inventory.getSkuId(),
                inventory.getStockQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getVersion());
    }

    private InventoryMovementResponse toMovementResponse(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.getMovementId(),
                movement.getSkuId(),
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getReason(),
                movement.getActorMemberId(),
                movement.getCreatedAt());
    }
}
