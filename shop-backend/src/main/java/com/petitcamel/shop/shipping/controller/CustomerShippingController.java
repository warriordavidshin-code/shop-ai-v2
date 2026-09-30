package com.petitcamel.shop.shipping.controller;

import com.petitcamel.shop.shipping.domain.ShipmentType;
import com.petitcamel.shop.shipping.dto.CustomerReturnInfo;
import com.petitcamel.shop.shipping.dto.ReturnCreateRequest;
import com.petitcamel.shop.shipping.dto.ReturnRequestResponse;
import com.petitcamel.shop.shipping.dto.ShippingPolicyResponse;
import com.petitcamel.shop.shipping.dto.ShippingQuote;
import com.petitcamel.shop.shipping.dto.TrackingResult;
import com.petitcamel.shop.shipping.service.ReturnService;
import com.petitcamel.shop.shipping.service.ShippingFeeService;
import com.petitcamel.shop.shipping.service.TrackingService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
public class CustomerShippingController {

    private final TrackingService trackingService;
    private final ReturnService returnService;
    private final ShippingFeeService shippingFeeService;

    public CustomerShippingController(
            TrackingService trackingService,
            ReturnService returnService,
            ShippingFeeService shippingFeeService) {
        this.trackingService = trackingService;
        this.returnService = returnService;
        this.shippingFeeService = shippingFeeService;
    }

    @GetMapping("/api/orders/{orderId}/tracking")
    public TrackingResult tracking(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "DELIVERY") ShipmentType type,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return trackingService.getTrackingForMember(principal.getMemberId(), orderId, type);
    }

    @GetMapping("/api/orders/{orderId}/return")
    public CustomerReturnInfo returnInfo(
            @PathVariable Long orderId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return returnService.getReturnInfo(principal.getMemberId(), orderId);
    }

    @PostMapping("/api/orders/{orderId}/return")
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnRequestResponse requestReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody ReturnCreateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return returnService.requestReturn(principal.getMemberId(), orderId, request);
    }

    @GetMapping("/api/shipping/policy")
    public ShippingPolicyResponse policy() {
        return shippingFeeService.getPolicy();
    }

    @GetMapping("/api/shipping/quote")
    public ShippingQuote quote(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String postcode) {
        return shippingFeeService.quote(amount, postcode);
    }
}
