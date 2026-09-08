package com.petitcamel.shop.payment.controller;

import com.petitcamel.shop.order.service.OrderService;
import com.petitcamel.shop.payment.dto.MockApproveRequest;
import com.petitcamel.shop.payment.dto.PaymentResponse;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final OrderService orderService;

    public PaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/mock/approve")
    public PaymentResponse approveMock(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody MockApproveRequest request) {
        return orderService.approveMockPayment(principal.getMemberId(), request.orderNo());
    }
}
