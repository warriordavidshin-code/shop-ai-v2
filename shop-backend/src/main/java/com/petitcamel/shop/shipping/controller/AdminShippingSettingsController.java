package com.petitcamel.shop.shipping.controller;

import com.petitcamel.shop.security.MemberPrincipal;
import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.dto.DeliveryCompanyResponse;
import com.petitcamel.shop.shipping.dto.ExtraAreaRequest;
import com.petitcamel.shop.shipping.dto.ExtraAreaResponse;
import com.petitcamel.shop.shipping.dto.ShippingIntegrationStatus;
import com.petitcamel.shop.shipping.dto.ShippingPolicyResponse;
import com.petitcamel.shop.shipping.dto.ShippingPolicyUpdateRequest;
import com.petitcamel.shop.shipping.provider.ShippingProviderRegistry;
import com.petitcamel.shop.shipping.service.DeliveryCompanyService;
import com.petitcamel.shop.shipping.service.ShipmentService;
import com.petitcamel.shop.shipping.service.ShippingFeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shipping")
public class AdminShippingSettingsController {

    public record EnabledRequest(boolean enabled) {
    }

    private final ShippingFeeService shippingFeeService;
    private final DeliveryCompanyService deliveryCompanyService;
    private final ShipmentService shipmentService;
    private final ShippingProviderRegistry providerRegistry;
    private final ShippingProperties properties;

    public AdminShippingSettingsController(
            ShippingFeeService shippingFeeService,
            DeliveryCompanyService deliveryCompanyService,
            ShipmentService shipmentService,
            ShippingProviderRegistry providerRegistry,
            ShippingProperties properties) {
        this.shippingFeeService = shippingFeeService;
        this.deliveryCompanyService = deliveryCompanyService;
        this.shipmentService = shipmentService;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
    }

    @GetMapping("/policy")
    public ShippingPolicyResponse policy() {
        return shippingFeeService.getPolicy();
    }

    @PutMapping("/policy")
    public ShippingPolicyResponse updatePolicy(
            @Valid @RequestBody ShippingPolicyUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return shippingFeeService.updatePolicy(request, actorId(principal));
    }

    @GetMapping("/extra-areas")
    public List<ExtraAreaResponse> extraAreas() {
        return shippingFeeService.listExtraAreas();
    }

    @PostMapping("/extra-areas")
    @ResponseStatus(HttpStatus.CREATED)
    public ExtraAreaResponse addExtraArea(
            @Valid @RequestBody ExtraAreaRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return shippingFeeService.addExtraArea(request, actorId(principal));
    }

    @DeleteMapping("/extra-areas/{areaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExtraArea(
            @PathVariable Long areaId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        shippingFeeService.deleteExtraArea(areaId, actorId(principal));
    }

    @GetMapping("/delivery-companies")
    public List<DeliveryCompanyResponse> deliveryCompanies() {
        return deliveryCompanyService.listAll();
    }

    @PatchMapping("/delivery-companies/{code}")
    public DeliveryCompanyResponse setCompanyEnabled(
            @PathVariable String code,
            @RequestBody EnabledRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return deliveryCompanyService.setEnabled(code, request.enabled(), actorId(principal));
    }

    @GetMapping("/integration")
    public ShippingIntegrationStatus integration() {
        return new ShippingIntegrationStatus(
                providerRegistry.requestedProvider(),
                providerRegistry.active().name(),
                providerRegistry.externalTrackingEnabled(),
                properties.getApiKey() != null && !properties.getApiKey().isBlank(),
                shipmentService.pickupServiceName(),
                shipmentService.waybillSupported(),
                properties.getTracking().isSchedulerEnabled(),
                properties.getTracking().getCron(),
                properties.getTracking().getCacheMinutes());
    }

    private static Long actorId(MemberPrincipal principal) {
        return principal == null ? null : principal.getMemberId();
    }
}
