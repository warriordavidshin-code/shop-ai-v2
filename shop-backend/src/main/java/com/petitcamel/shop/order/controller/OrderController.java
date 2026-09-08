package com.petitcamel.shop.order.controller;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.order.dto.CreateOrderRequest;
import com.petitcamel.shop.order.dto.OrderResponse;
import com.petitcamel.shop.order.dto.OrderSummaryResponse;
import com.petitcamel.shop.order.service.OrderService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(principal.getMemberId(), idempotencyKey, request);
    }

    @GetMapping("/api/orders/{orderNo}")
    public OrderResponse getOrder(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String orderNo) {
        return orderService.getOrder(principal.getMemberId(), orderNo);
    }

    @GetMapping("/api/members/me/orders")
    public PageResponse<OrderSummaryResponse> listMyOrders(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.listMyOrders(principal.getMemberId(), page, size);
    }

    @PostMapping("/api/orders/{orderNo}/cancel")
    public OrderResponse cancelOrder(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String orderNo) {
        return orderService.cancelOrder(principal.getMemberId(), orderNo);
    }
}
