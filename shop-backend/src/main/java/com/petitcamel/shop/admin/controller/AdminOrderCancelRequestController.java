package com.petitcamel.shop.admin.controller;

import com.petitcamel.shop.admin.dto.AdminCancelRequestResponse;
import com.petitcamel.shop.admin.dto.CancelRejectRequest;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.order.domain.CancelRequestStatus;
import com.petitcamel.shop.order.service.OrderCancelRequestService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/order-cancel-requests")
public class AdminOrderCancelRequestController {

    private final OrderCancelRequestService orderCancelRequestService;

    public AdminOrderCancelRequestController(OrderCancelRequestService orderCancelRequestService) {
        this.orderCancelRequestService = orderCancelRequestService;
    }

    @GetMapping
    public PageResponse<AdminCancelRequestResponse> list(
            @RequestParam(required = false) CancelRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderCancelRequestService.listForAdmin(status, page, size);
    }

    @PostMapping("/{cancelRequestId}/approve")
    public AdminCancelRequestResponse approve(
            @PathVariable Long cancelRequestId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return orderCancelRequestService.approve(cancelRequestId, principal.getMemberId());
    }

    @PostMapping("/{cancelRequestId}/reject")
    public AdminCancelRequestResponse reject(
            @PathVariable Long cancelRequestId,
            @Valid @RequestBody CancelRejectRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return orderCancelRequestService.reject(cancelRequestId, principal.getMemberId(), request.reason());
    }
}
