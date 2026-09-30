package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import com.petitcamel.shop.shipping.domain.ShippingApiOperation;
import com.petitcamel.shop.shipping.domain.ShippingOperationType;
import com.petitcamel.shop.shipping.domain.ShippingOperationStatus;
import com.petitcamel.shop.shipping.dto.AdminReturnResponse;
import com.petitcamel.shop.shipping.dto.ShipmentActionResponse;
import com.petitcamel.shop.shipping.dto.WaybillResponse;
import com.petitcamel.shop.shipping.provider.ProviderActionResult;
import com.petitcamel.shop.shipping.provider.ShipmentCommand;
import com.petitcamel.shop.shipping.provider.ShippingProviderClient;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.provider.ShippingProviderRegistry;
import com.petitcamel.shop.shipping.repository.ShippingApiOperationRepository;
import com.petitcamel.shop.shipping.support.ShippingLogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * State-changing calls to shipping vendors: waybill issue, delivery pickup and return pickup.
 *
 * <p>Flow for an external vendor: read the target (short transaction) → claim the idempotency key in
 * {@code shipping_api_operation} (own transaction) → call the vendor (no transaction) → store the sanitized result
 * (own transaction) → apply it to the shipment and mark the operation applied (one business transaction).
 * If the apply step fails, the stored result is reused on the next click, so the vendor is never asked twice
 * (e.g. never two waybills for one order). The MANUAL vendor only tells the admin what to do, so it is not logged.
 */
@Service
public class ShipmentIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentIntegrationService.class);

    static final String UNKNOWN_OUTCOME_MESSAGE =
            "이전 요청의 처리 결과를 확인할 수 없습니다. 업체 관리화면에서 처리 여부를 확인한 뒤, "
                    + "배송 설정의 [API 작업 로그]에서 '재시도 허용'을 누르거나 송장번호를 직접 등록해 주세요.";

    private final ShippingProviderRegistry registry;
    private final ShippingOperationService operationService;
    private final ShipmentService shipmentService;
    private final ReturnService returnService;
    private final DeliveryCompanyService deliveryCompanyService;
    private final ShippingApiOperationRepository operationRepository;
    private final AuditLogService auditLogService;
    private final ShippingProperties properties;

    public ShipmentIntegrationService(
            ShippingProviderRegistry registry,
            ShippingOperationService operationService,
            ShipmentService shipmentService,
            ReturnService returnService,
            DeliveryCompanyService deliveryCompanyService,
            ShippingApiOperationRepository operationRepository,
            AuditLogService auditLogService,
            ShippingProperties properties) {
        this.registry = registry;
        this.operationService = operationService;
        this.shipmentService = shipmentService;
        this.returnService = returnService;
        this.deliveryCompanyService = deliveryCompanyService;
        this.operationRepository = operationRepository;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ delivery

    /** "[송장 발급]": asks the waybill vendor for an invoice number. */
    public ShipmentActionResponse issueWaybill(Long orderId, String companyInput, Long actorId) {
        ShippingProviderRegistry.ActiveProvider provider = registry.resolve(ProviderCapability.WAYBILL)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "송장 자동발급이 가능한 배송 API 업체가 없습니다. 택배사 사이트에서 발급한 뒤 송장번호를 등록해 주세요."));
        ShipmentService.ExternalTarget target =
                shipmentService.prepareDeliveryAction(orderId, companyInput, ShipmentStatus.WAYBILL_ISSUED);
        if (target.alreadyDone()) {
            return shipmentService.alreadyDone(target.shipmentId(), "이미 송장이 발급되었습니다.");
        }
        ShipmentActionResponse response = execute(provider, ShippingOperationType.WAYBILL_ISSUE,
                ShippingOperationService.waybillKey(orderId), target, true,
                ShippingProviderClient::issueWaybill,
                (opId, result) -> {
                    if (result.trackingNumber() == null || result.trackingNumber().isBlank()) {
                        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "업체 응답에 송장번호가 없습니다.");
                    }
                    return shipmentService.applyDeliveryResult(target, opId, provider.id(), result,
                            ShipmentStatus.WAYBILL_ISSUED, "송장이 발급되었습니다. (" + provider.settings().getName() + ")",
                            actorId);
                },
                () -> shipmentService.alreadyDone(target.shipmentId(), "이미 송장이 발급되었습니다."));
        auditLogService.record(actorId, "SHIPMENT_WAYBILL_ISSUE", "ORDER", target.orderNo(),
                "provider=" + provider.code() + ", company=" + target.deliveryCompanyCode());
        return response;
    }

    /** "[집하 요청]": books the courier pickup for a delivery (MANUAL = admin books it by phone / courier site). */
    public ShipmentActionResponse requestDeliveryPickup(Long orderId, String companyInput, Long actorId) {
        ShippingProviderRegistry.ActiveProvider provider = requirePickupProvider(ProviderCapability.PICKUP);
        ShipmentService.ExternalTarget target =
                shipmentService.prepareDeliveryAction(orderId, companyInput, ShipmentStatus.PICKUP_REQUESTED);
        if (target.alreadyDone()) {
            return shipmentService.alreadyDone(target.shipmentId(), "이미 집하가 요청되었습니다.");
        }
        ShipmentActionResponse response = execute(provider, ShippingOperationType.PICKUP_REQUEST,
                ShippingOperationService.pickupKey(target.shipmentId()), target, !provider.isManual(),
                ShippingProviderClient::requestPickup,
                (opId, result) -> shipmentService.applyDeliveryResult(target, opId,
                        provider.isManual() ? null : provider.id(), result,
                        ShipmentStatus.PICKUP_REQUESTED, "택배 집하를 요청했습니다.", actorId),
                () -> shipmentService.alreadyDone(target.shipmentId(), "이미 집하가 요청되었습니다."));
        auditLogService.record(actorId, "SHIPMENT_PICKUP_REQUEST", "ORDER", target.orderNo(),
                "provider=" + provider.code() + ", company=" + target.deliveryCompanyCode());
        return response;
    }

    /** Waybill label of an invoice issued through a vendor. */
    public WaybillResponse printWaybill(Long orderId) {
        ShippingApiOperation op = operationRepository.findByIdempotencyKey(ShippingOperationService.waybillKey(orderId))
                .filter(o -> o.getStatus() == ShippingOperationStatus.SUCCEEDED)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "배송 API로 발급된 송장이 없습니다. 택배사 사이트에서 출력해 주세요."));
        ShippingProviderClient client = registry.client(op.getProviderCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "송장 출력을 지원하지 않는 업체입니다."));
        ProviderActionResult stored = operationService.storedResult(op);
        try {
            ProviderActionResult result = client.printWaybill(op.getExternalReference(), stored.trackingNumber());
            return new WaybillResponse(stored.trackingNumber(), result.printUrl(), result.message());
        } catch (ShippingProviderException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, safeMessage(ex));
        }
    }

    // ------------------------------------------------------------------ returns

    /**
     * "[반품수거 요청]": books the return pickup at the customer's address.
     *
     * @param trackingInput invoice typed by the admin for a manual booking; ignored when the vendor issues one
     */
    public AdminReturnResponse requestReturnPickup(Long returnRequestId, String companyInput, String trackingInput, Long actorId) {
        ShippingProviderRegistry.ActiveProvider provider = requirePickupProvider(ProviderCapability.RETURN_PICKUP);
        ShipmentService.ExternalTarget target = returnService.prepareReturnPickup(returnRequestId, companyInput);
        return execute(provider, ShippingOperationType.RETURN_PICKUP,
                ShippingOperationService.returnPickupKey(returnRequestId), target, !provider.isManual(),
                ShippingProviderClient::requestReturnPickup,
                (opId, result) -> returnService.applyReturnPickup(target, opId,
                        provider.isManual() ? null : provider.id(), provider.code(), result, trackingInput, actorId),
                () -> returnService.get(returnRequestId));
    }

    // ------------------------------------------------------------------ core flow

    private <R> R execute(
            ShippingProviderRegistry.ActiveProvider provider,
            ShippingOperationType type,
            String idempotencyKey,
            ShipmentService.ExternalTarget target,
            boolean requireCompanyCode,
            BiFunction<ShippingProviderClient, ShipmentCommand, ProviderActionResult> call,
            BiFunction<Long, ProviderActionResult, R> apply,
            Supplier<R> alreadyApplied) {
        String externalCode = resolveExternalCode(provider, target, requireCompanyCode);
        Function<String, ShipmentCommand> command = requestId -> new ShipmentCommand(
                requestId, target.orderId(), target.orderNo(), target.shipmentId(), target.shipmentType(),
                target.deliveryCompanyCode(), externalCode, target.contact(), target.memo());

        if (provider.isManual()) {
            ProviderActionResult result = callVendor(provider, call, command.apply(null), null);
            return apply.apply(null, result);
        }

        ShippingOperationService.Start start = operationService.start(new ShippingOperationService.Request(
                provider.code(), type, target.orderId(), target.shipmentId(), target.returnRequestId(), idempotencyKey));
        ProviderActionResult result;
        switch (start.outcome()) {
            case IN_PROGRESS -> throw new BusinessException(ErrorCode.CONFLICT,
                    "같은 요청이 처리 중입니다. 잠시 후 다시 확인해 주세요.");
            case ALREADY_SUCCEEDED -> {
                if (start.applied()) {
                    return alreadyApplied.get();
                }
                log.info("[SHIPPING] reusing stored vendor result type={} orderId={} operationId={}",
                        type, target.orderId(), start.operationId());
                result = start.storedResult();
            }
            case IN_DOUBT -> {
                Optional<ProviderActionResult> found = lookup(provider, type, start.requestId());
                if (found.isEmpty()) {
                    operationService.unknown(start.operationId(), "OUTCOME_UNKNOWN", "이전 요청 결과 확인 불가");
                    throw new BusinessException(ErrorCode.CONFLICT, UNKNOWN_OUTCOME_MESSAGE);
                }
                result = found.get();
                operationService.succeeded(start.operationId(), result);
            }
            default -> {
                result = callVendor(provider, call, command.apply(start.requestId()), start.operationId());
                operationService.succeeded(start.operationId(), result);
            }
        }

        try {
            return apply.apply(start.operationId(), result);
        } catch (BusinessException | DataAccessException ex) {
            log.warn("[SHIPPING] vendor succeeded but saving failed type={} orderId={} operationId={} trackingNumber={} error={}",
                    type, target.orderId(), start.operationId(),
                    ShippingLogMasker.maskTrackingNumber(result.trackingNumber()), ex.getClass().getSimpleName());
            String reason = ex instanceof BusinessException be ? be.getMessage() : "저장 중 충돌이 발생했습니다.";
            throw new BusinessException(ErrorCode.CONFLICT,
                    "업체 처리는 완료되었지만 저장에 실패했습니다: " + reason
                            + " 원인을 해결한 뒤 같은 버튼을 다시 누르면 업체를 다시 호출하지 않고 저장합니다.");
        }
    }

    private ProviderActionResult callVendor(
            ShippingProviderRegistry.ActiveProvider provider,
            BiFunction<ShippingProviderClient, ShipmentCommand, ProviderActionResult> call,
            ShipmentCommand command,
            Long operationId) {
        try {
            ProviderActionResult result = call.apply(provider.client(), command);
            if (result == null) {
                throw new ShippingProviderException("업체 응답이 비어 있습니다.", null, "EMPTY_RESPONSE", true);
            }
            return result;
        } catch (ShippingProviderException ex) {
            if (operationId != null) {
                operationService.failed(operationId, scrubbed(ex));
            }
            log.warn("[SHIPPING] provider={} call failed orderId={} outcomeUnknown={} error={}",
                    provider.code(), command.orderId(), ex.isOutcomeUnknown(), ex.getErrorCode());
            throw new BusinessException(ex.isOutcomeUnknown() ? ErrorCode.CONFLICT : ErrorCode.BUSINESS_RULE_VIOLATION,
                    ex.isOutcomeUnknown() ? UNKNOWN_OUTCOME_MESSAGE : safeMessage(ex));
        } catch (RuntimeException ex) {
            if (operationId != null) {
                operationService.unknown(operationId, ex.getClass().getSimpleName(), "예상하지 못한 오류");
            }
            log.warn("[SHIPPING] provider={} unexpected error orderId={} error={}",
                    provider.code(), command.orderId(), ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.CONFLICT, UNKNOWN_OUTCOME_MESSAGE);
        }
    }

    private Optional<ProviderActionResult> lookup(
            ShippingProviderRegistry.ActiveProvider provider, ShippingOperationType type, String requestId) {
        try {
            return provider.client().lookup(type, requestId);
        } catch (RuntimeException ex) {
            log.warn("[SHIPPING] provider={} lookup failed type={} error={}", provider.code(), type,
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String resolveExternalCode(
            ShippingProviderRegistry.ActiveProvider provider, ShipmentService.ExternalTarget target, boolean required) {
        if (provider.isManual()) {
            return null;
        }
        if (target.deliveryCompanyId() == null) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "택배사를 선택해 주세요.");
            }
            return null;
        }
        String code = deliveryCompanyService.externalCode(target.deliveryCompanyId(), provider.id()).orElse(null);
        if (code == null && required) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    provider.settings().getName() + "에 등록된 택배사 코드가 없습니다. 배송 설정에서 택배사 코드를 등록해 주세요.");
        }
        return code;
    }

    private ShippingProviderRegistry.ActiveProvider requirePickupProvider(ProviderCapability capability) {
        return registry.resolve(capability)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        capability.getLabel() + "을(를) 처리할 배송 API 업체가 없습니다. 배송 설정을 확인해 주세요."));
    }

    private ShippingProviderException scrubbed(ShippingProviderException ex) {
        return new ShippingProviderException(safeMessage(ex), ex.getHttpStatus(), ex.getErrorCode(), ex.isOutcomeUnknown());
    }

    private String safeMessage(ShippingProviderException ex) {
        String message = ex.getMessage() == null ? "배송 API 호출에 실패했습니다." : ex.getMessage();
        return ShippingLogMasker.scrub(message, properties.getApiKey());
    }
}
