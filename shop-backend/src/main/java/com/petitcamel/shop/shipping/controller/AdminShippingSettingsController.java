package com.petitcamel.shop.shipping.controller;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.security.MemberPrincipal;
import com.petitcamel.shop.shipping.dto.ConnectionTestResponse;
import com.petitcamel.shop.shipping.dto.DeliveryCompanyResponse;
import com.petitcamel.shop.shipping.dto.ExtraAreaRequest;
import com.petitcamel.shop.shipping.dto.ExtraAreaResponse;
import com.petitcamel.shop.shipping.dto.ProviderCodeUpdateRequest;
import com.petitcamel.shop.shipping.dto.ShippingApiOperationResponse;
import com.petitcamel.shop.shipping.dto.ShippingIntegrationStatus;
import com.petitcamel.shop.shipping.dto.ShippingPolicyResponse;
import com.petitcamel.shop.shipping.dto.ShippingPolicyUpdateRequest;
import com.petitcamel.shop.shipping.dto.ShippingProviderResponse;
import com.petitcamel.shop.shipping.dto.ShippingProviderUpdateRequest;
import com.petitcamel.shop.shipping.service.DeliveryCompanyService;
import com.petitcamel.shop.shipping.service.ShippingFeeService;
import com.petitcamel.shop.shipping.service.ShippingOperationService;
import com.petitcamel.shop.shipping.service.ShippingProviderService;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ShippingProviderService providerService;
    private final ShippingOperationService operationService;

    public AdminShippingSettingsController(
            ShippingFeeService shippingFeeService,
            DeliveryCompanyService deliveryCompanyService,
            ShippingProviderService providerService,
            ShippingOperationService operationService) {
        this.shippingFeeService = shippingFeeService;
        this.deliveryCompanyService = deliveryCompanyService;
        this.providerService = providerService;
        this.operationService = operationService;
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

    @PatchMapping("/extra-areas/{areaId}")
    public ExtraAreaResponse setExtraAreaEnabled(
            @PathVariable Long areaId,
            @RequestBody EnabledRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return shippingFeeService.setExtraAreaEnabled(areaId, request.enabled(), actorId(principal));
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

    /** Courier code in a vendor's code system; a blank code removes the mapping. */
    @PutMapping("/delivery-companies/{code}/provider-codes/{providerCode}")
    public DeliveryCompanyResponse updateProviderCode(
            @PathVariable String code,
            @PathVariable String providerCode,
            @Valid @RequestBody ProviderCodeUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return deliveryCompanyService.updateProviderCode(code, providerCode, request.externalCompanyCode(),
                actorId(principal));
    }

    @GetMapping("/providers")
    public List<ShippingProviderResponse> providers() {
        return providerService.list();
    }

    @PatchMapping("/providers/{code}")
    public ShippingProviderResponse updateProvider(
            @PathVariable String code,
            @RequestBody ShippingProviderUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return providerService.update(code, request, actorId(principal));
    }

    @PostMapping("/providers/{code}/test")
    public ConnectionTestResponse testProvider(
            @PathVariable String code,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return providerService.testConnection(code, actorId(principal));
    }

    /** External API call log. {@code status}: ALL, ATTENTION (needs a decision) or an operation status. */
    @GetMapping("/operations")
    public PageResponse<ShippingApiOperationResponse> operations(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operationService.list(status, page, size);
    }

    /** After checking the vendor's console: lets the next click call the vendor again. */
    @PostMapping("/operations/{operationId}/allow-retry")
    public ShippingApiOperationResponse allowRetry(
            @PathVariable Long operationId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return operationService.allowRetry(operationId, actorId(principal));
    }

    @GetMapping("/integration")
    public ShippingIntegrationStatus integration() {
        return providerService.integrationStatus();
    }

    private static Long actorId(MemberPrincipal principal) {
        return principal == null ? null : principal.getMemberId();
    }
}
