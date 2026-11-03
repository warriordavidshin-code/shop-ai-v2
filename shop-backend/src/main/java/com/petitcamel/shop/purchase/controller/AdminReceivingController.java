package com.petitcamel.shop.purchase.controller;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.inventory.dto.InventoryAnalyticsDtos;
import com.petitcamel.shop.inventory.service.InventoryAnalyticsService;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus;
import com.petitcamel.shop.purchase.dto.ReceivingDtos;
import com.petitcamel.shop.purchase.service.ReceivingService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/inventory")
public class AdminReceivingController {

    private final ReceivingService receivingService;
    private final InventoryAnalyticsService analyticsService;

    public AdminReceivingController(ReceivingService receivingService, InventoryAnalyticsService analyticsService) {
        this.receivingService = receivingService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/receipts")
    public PageResponse<ReceivingDtos.ReceiptResponse> receipts(
            @RequestParam(required = false) ReceiptStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return receivingService.list(status, page, size);
    }

    @GetMapping("/receipts/{id}")
    public ReceivingDtos.ReceiptResponse receipt(@PathVariable Long id) {
        return receivingService.get(id);
    }

    @PostMapping("/receipts")
    public ReceivingDtos.ReceiptResponse create(@AuthenticationPrincipal MemberPrincipal principal,
                                                @Valid @RequestBody ReceivingDtos.CreateReceiptRequest request) {
        return receivingService.create(request, principal.getMemberId());
    }

    @PostMapping("/receipts/{id}/confirm")
    public ReceivingDtos.ReceiptResponse confirm(@AuthenticationPrincipal MemberPrincipal principal,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody(required = false)
                                                 ReceivingDtos.ConfirmReceiptRequest request) {
        return receivingService.confirm(id, request, principal.getMemberId());
    }

    @PostMapping("/receipts/{id}/cancel")
    public ReceivingDtos.ReceiptResponse cancel(@AuthenticationPrincipal MemberPrincipal principal,
                                                @PathVariable Long id) {
        return receivingService.cancel(id, principal.getMemberId());
    }

    @GetMapping("/transactions")
    public PageResponse<InventoryAnalyticsDtos.TransactionRow> transactions(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return analyticsService.transactions(skuId, productId, type, from, to, keyword, page, size);
    }

    @GetMapping("/status")
    public InventoryAnalyticsDtos.StockStatusResponse status(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String filter) {
        return analyticsService.status(keyword, filter);
    }

    @GetMapping("/low-stock")
    public InventoryAnalyticsDtos.AnalysisResponse<InventoryAnalyticsDtos.LowStockRow> lowStock() {
        return analyticsService.lowStock();
    }

    @GetMapping("/slow-moving")
    public InventoryAnalyticsDtos.AnalysisResponse<InventoryAnalyticsDtos.SlowMovingRow> slowMoving(
            @RequestParam(required = false) Integer days) {
        return analyticsService.slowMoving(days);
    }
}
