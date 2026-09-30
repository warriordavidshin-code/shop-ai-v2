package com.petitcamel.shop.shipping.controller;

import com.petitcamel.shop.security.MemberPrincipal;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import com.petitcamel.shop.shipping.dto.BulkInvoiceRequest;
import com.petitcamel.shop.shipping.dto.BulkInvoiceResponse;
import com.petitcamel.shop.shipping.dto.InvoiceRegisterRequest;
import com.petitcamel.shop.shipping.dto.ReturnActionRequests;
import com.petitcamel.shop.shipping.dto.ShipmentActionResponse;
import com.petitcamel.shop.shipping.dto.ShipmentStatusChangeRequest;
import com.petitcamel.shop.shipping.service.ShipmentService;
import com.petitcamel.shop.shipping.service.TrackingService;
import com.petitcamel.shop.shipping.waybill.WaybillResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminShipmentController {

    private final ShipmentService shipmentService;
    private final TrackingService trackingService;

    public AdminShipmentController(ShipmentService shipmentService, TrackingService trackingService) {
        this.shipmentService = shipmentService;
        this.trackingService = trackingService;
    }

    /** 송장 등록/수정. Registering an invoice starts delivery (배송중). */
    @PutMapping("/orders/{orderId}/shipment")
    public ShipmentActionResponse registerInvoice(
            @PathVariable Long orderId,
            @Valid @RequestBody InvoiceRegisterRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return shipmentService.registerInvoice(orderId, request.deliveryCompany(), request.trackingNumber(),
                actorId(principal));
    }

    @PatchMapping("/orders/{orderId}/shipment/status")
    public ShipmentActionResponse changeStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody ShipmentStatusChangeRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return shipmentService.changeStatus(orderId, request.status(), actorId(principal));
    }

    @PostMapping("/orders/{orderId}/shipment/pickup-request")
    public ShipmentActionResponse requestPickup(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) ReturnActionRequests.Pickup request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return shipmentService.requestPickup(orderId, request == null ? null : request.deliveryCompany(),
                actorId(principal));
    }

    @PostMapping("/orders/{orderId}/shipment/refresh")
    public ShipmentActionResponse refresh(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "DELIVERY") ShipmentType type) {
        return trackingService.adminRefresh(orderId, type);
    }

    /** 송장 발급 (requires a waybill integration such as Goodsflow). */
    @PostMapping("/orders/{orderId}/shipment/waybill")
    public WaybillResponse issueWaybill(@PathVariable Long orderId) {
        return shipmentService.issueWaybill(orderId);
    }

    /** 송장 출력 (requires a waybill integration such as Goodsflow). */
    @PostMapping("/orders/{orderId}/shipment/waybill/print")
    public WaybillResponse printWaybill(@PathVariable Long orderId) {
        return shipmentService.printWaybill(orderId);
    }

    /** 송장 일괄 등록 (checkbox selection or CSV upload parsed by the admin UI). */
    @PostMapping("/shipments/bulk")
    public BulkInvoiceResponse bulkRegister(
            @Valid @RequestBody BulkInvoiceRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return shipmentService.bulkRegister(request.items(), actorId(principal));
    }

    private static Long actorId(MemberPrincipal principal) {
        return principal == null ? null : principal.getMemberId();
    }
}
