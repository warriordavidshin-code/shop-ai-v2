package com.petitcamel.shop.admin.controller;

import com.petitcamel.shop.admin.dto.AdminOrderSummaryResponse;
import com.petitcamel.shop.admin.dto.OrderStatusUpdateRequest;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.dto.OrderResponse;
import com.petitcamel.shop.order.service.OrderService;
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
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<AdminOrderSummaryResponse> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        return orderService.listAdminOrders(page, size, status);
    }

    @PatchMapping("/{orderNo}/status")
    public OrderResponse updateStatus(
            @PathVariable String orderNo,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        Long actorId = principal == null ? null : principal.getMemberId();
        return orderService.updateAdminOrderStatus(orderNo, request.status(), actorId);
    }
}
