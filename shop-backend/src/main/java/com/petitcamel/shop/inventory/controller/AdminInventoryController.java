package com.petitcamel.shop.inventory.controller;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.inventory.dto.InventoryAdjustRequest;
import com.petitcamel.shop.inventory.dto.InventoryMovementResponse;
import com.petitcamel.shop.inventory.dto.InventoryResponse;
import com.petitcamel.shop.inventory.service.InventoryService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PatchMapping("/inventories/{skuId}/adjust")
    public InventoryResponse adjust(
            @PathVariable Long skuId,
            @Valid @RequestBody InventoryAdjustRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        Long actorId = principal == null ? null : principal.getMemberId();
        return inventoryService.adjust(skuId, request, actorId);
    }

    @GetMapping("/inventory-movements")
    public PageResponse<InventoryMovementResponse> listMovements(
            @RequestParam(required = false) Long skuId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.listMovements(skuId, page, size);
    }
}
