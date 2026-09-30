package com.petitcamel.shop.shipping.controller;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.security.MemberPrincipal;
import com.petitcamel.shop.shipping.dto.AdminReturnResponse;
import com.petitcamel.shop.shipping.dto.InvoiceRegisterRequest;
import com.petitcamel.shop.shipping.dto.ReturnActionRequests;
import com.petitcamel.shop.shipping.service.ReturnService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/returns")
public class AdminReturnController {

    private final ReturnService returnService;

    public AdminReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    /** @param status ALL, OPEN or a ReturnStatus name */
    @GetMapping
    public PageResponse<AdminReturnResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return returnService.list(status, page, size);
    }

    @GetMapping("/{returnRequestId}")
    public AdminReturnResponse get(@PathVariable Long returnRequestId) {
        return returnService.get(returnRequestId);
    }

    @PostMapping("/{returnRequestId}/approve")
    public AdminReturnResponse approve(
            @PathVariable Long returnRequestId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return returnService.approve(returnRequestId, actorId(principal));
    }

    @PostMapping("/{returnRequestId}/reject")
    public AdminReturnResponse reject(
            @PathVariable Long returnRequestId,
            @Valid @RequestBody ReturnActionRequests.Reject request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return returnService.reject(returnRequestId, request.reason(), actorId(principal));
    }

    /** [반품수거 요청] */
    @PostMapping("/{returnRequestId}/pickup-request")
    public AdminReturnResponse requestPickup(
            @PathVariable Long returnRequestId,
            @Valid @RequestBody(required = false) ReturnActionRequests.Pickup request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return returnService.requestPickup(returnRequestId,
                request == null ? null : request.deliveryCompany(),
                request == null ? null : request.trackingNumber(),
                actorId(principal));
    }

    @PutMapping("/{returnRequestId}/tracking")
    public AdminReturnResponse registerTracking(
            @PathVariable Long returnRequestId,
            @Valid @RequestBody InvoiceRegisterRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return returnService.registerTracking(returnRequestId, request.deliveryCompany(), request.trackingNumber(),
                actorId(principal));
    }

    @PatchMapping("/{returnRequestId}/status")
    public AdminReturnResponse changeStatus(
            @PathVariable Long returnRequestId,
            @Valid @RequestBody ReturnActionRequests.StatusChange request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return returnService.changeStatus(returnRequestId, request.status(), actorId(principal));
    }

    @PostMapping("/{returnRequestId}/refund")
    public AdminReturnResponse refund(
            @PathVariable Long returnRequestId,
            @Valid @RequestBody ReturnActionRequests.Refund request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return returnService.refund(returnRequestId, request.restock(), request.adminMemo(), actorId(principal));
    }

    private static Long actorId(MemberPrincipal principal) {
        return principal == null ? null : principal.getMemberId();
    }
}
