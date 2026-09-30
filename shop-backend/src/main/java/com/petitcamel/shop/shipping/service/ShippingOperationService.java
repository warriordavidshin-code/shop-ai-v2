package com.petitcamel.shop.shipping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.shipping.domain.ShippingApiOperation;
import com.petitcamel.shop.shipping.domain.ShippingOperationStatus;
import com.petitcamel.shop.shipping.domain.ShippingOperationType;
import com.petitcamel.shop.shipping.dto.ShippingApiOperationResponse;
import com.petitcamel.shop.shipping.provider.ProviderActionResult;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.repository.ShippingApiOperationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Log and idempotency guard for external shipping API calls. Every state change of an operation row is committed
 * in its own transaction, so the row survives when the caller's business transaction rolls back. The one exception
 * is {@link #markApplied}, which joins the business transaction: "applied" is true exactly when the shipment change
 * was committed.
 */
@Service
public class ShippingOperationService {

    /** A PENDING row younger than this is treated as a call still running. */
    static final Duration IN_PROGRESS_WINDOW = Duration.ofMinutes(2);

    public enum StartOutcome {
        /** New (or retried after failure) call: the caller should invoke the vendor. */
        STARTED,
        /** The vendor already succeeded; reuse {@link Start#storedResult()} instead of calling again. */
        ALREADY_SUCCEEDED,
        /** Another request with the same key is running right now. */
        IN_PROGRESS,
        /** An earlier call's outcome is unknown; look it up at the vendor before doing anything. */
        IN_DOUBT
    }

    public record Request(
            String providerCode,
            ShippingOperationType type,
            Long orderId,
            Long shipmentId,
            Long returnRequestId,
            String idempotencyKey
    ) {
    }

    public record Start(
            Long operationId,
            String requestId,
            StartOutcome outcome,
            ProviderActionResult storedResult,
            boolean applied
    ) {
    }

    private final ShippingApiOperationRepository repository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTx;
    private final Clock clock;

    public ShippingOperationService(
            ShippingApiOperationRepository repository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public static String waybillKey(Long orderId) {
        return "WAYBILL:" + orderId + ":DELIVERY";
    }

    public static String pickupKey(Long shipmentId) {
        return "PICKUP:" + shipmentId;
    }

    public static String returnPickupKey(Long returnRequestId) {
        return "RETURN_PICKUP:" + returnRequestId;
    }

    public Start start(Request request) {
        try {
            return newTx.execute(status -> doStart(request));
        } catch (DataIntegrityViolationException ex) {
            // Another request inserted the same idempotency key between our lookup and insert.
            return new Start(null, null, StartOutcome.IN_PROGRESS, null, false);
        }
    }

    public void succeeded(Long operationId, ProviderActionResult result) {
        update(operationId, op -> {
            op.setStatus(ShippingOperationStatus.SUCCEEDED);
            op.setCompletedAt(clock.instant());
            op.setExternalReference(truncate(result.externalReference(), 100));
            op.setResponseSummary(summarize(result));
            op.setHttpStatus(null);
            op.setErrorCode(null);
            op.setErrorMessage(null);
        });
    }

    public void failed(Long operationId, ShippingProviderException ex) {
        update(operationId, op -> {
            op.setStatus(ex.isOutcomeUnknown() ? ShippingOperationStatus.UNKNOWN : ShippingOperationStatus.FAILED);
            op.setCompletedAt(clock.instant());
            op.setHttpStatus(ex.getHttpStatus());
            op.setErrorCode(truncate(ex.getErrorCode(), 50));
            op.setErrorMessage(truncate(ex.getMessage(), 300));
        });
    }

    public void unknown(Long operationId, String errorCode, String message) {
        update(operationId, op -> {
            op.setStatus(ShippingOperationStatus.UNKNOWN);
            op.setCompletedAt(clock.instant());
            op.setErrorCode(truncate(errorCode, 50));
            op.setErrorMessage(truncate(message, 300));
        });
    }

    /** Marks the vendor result as saved; must run inside the transaction that saved it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markApplied(Long operationId) {
        if (operationId == null) {
            return;
        }
        ShippingApiOperation op = repository.findById(operationId).orElseThrow();
        op.setAppliedAt(clock.instant());
        repository.save(op);
    }

    /** Stand-alone record without idempotency (failed tracking calls, connection tests). */
    public void record(Request request, ShippingOperationStatus status, ShippingProviderException error, String message) {
        newTx.executeWithoutResult(tx -> {
            Instant now = clock.instant();
            ShippingApiOperation op = newOperation(request, now);
            op.setStatus(status);
            op.setCompletedAt(now);
            if (error != null) {
                op.setHttpStatus(error.getHttpStatus());
                op.setErrorCode(truncate(error.getErrorCode(), 50));
                op.setErrorMessage(truncate(error.getMessage(), 300));
            } else if (message != null) {
                op.setResponseSummary(truncate(objectMapper.createObjectNode().put("message", message).toString(), 1000));
            }
            repository.save(op);
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<ShippingApiOperationResponse> list(String statusFilter, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100));
        Page<ShippingApiOperation> result;
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            result = repository.findAllByOrderByRequestedAtDesc(pageable);
        } else if ("ATTENTION".equalsIgnoreCase(statusFilter)) {
            result = repository.findByStatusInOrderByRequestedAtDesc(
                    EnumSet.of(ShippingOperationStatus.PENDING, ShippingOperationStatus.UNKNOWN), pageable);
        } else {
            result = repository.findByStatusInOrderByRequestedAtDesc(List.of(parseStatus(statusFilter)), pageable);
        }
        List<ShippingApiOperationResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * After an admin confirmed at the vendor that an UNKNOWN / stuck call did not go through, allows the next click
     * to call the vendor again.
     */
    @Transactional
    public ShippingApiOperationResponse allowRetry(Long operationId, Long actorMemberId) {
        ShippingApiOperation op = repository.findById(operationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "API 작업을 찾을 수 없습니다."));
        if (op.getStatus() != ShippingOperationStatus.UNKNOWN && op.getStatus() != ShippingOperationStatus.PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "결과 확인이 필요한 작업만 재시도를 허용할 수 있습니다.");
        }
        op.setStatus(ShippingOperationStatus.FAILED);
        op.setCompletedAt(clock.instant());
        op.setErrorCode("RETRY_ALLOWED");
        op.setErrorMessage("관리자가 업체 측 미처리를 확인하고 재시도를 허용했습니다.");
        repository.save(op);
        auditLogService.record(actorMemberId, "SHIPPING_API_RETRY_ALLOW", "SHIPPING_API_OPERATION",
                String.valueOf(op.getId()), "type=" + op.getOperationType() + ", key=" + op.getIdempotencyKey());
        return toResponse(op);
    }

    /** Rebuilds the vendor result saved by {@link #succeeded}. */
    ProviderActionResult storedResult(ShippingApiOperation op) {
        String trackingNumber = null;
        String message = null;
        boolean automatic = true;
        if (op.getResponseSummary() != null) {
            try {
                JsonNode node = objectMapper.readTree(op.getResponseSummary());
                trackingNumber = node.hasNonNull("trackingNumber") ? node.get("trackingNumber").asText() : null;
                message = node.hasNonNull("message") ? node.get("message").asText() : null;
                automatic = node.path("automatic").asBoolean(true);
            } catch (Exception ignored) {
                // Unreadable summary: fall back to the external reference only.
            }
        }
        return new ProviderActionResult(automatic, trackingNumber, op.getExternalReference(), null, message);
    }

    private Start doStart(Request request) {
        Instant now = clock.instant();
        ShippingApiOperation op = repository.findForUpdateByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (op == null) {
            op = newOperation(request, now);
            op.setStatus(ShippingOperationStatus.PENDING);
            repository.saveAndFlush(op);
            return new Start(op.getId(), op.getRequestId(), StartOutcome.STARTED, null, false);
        }
        return switch (op.getStatus()) {
            case SUCCEEDED -> new Start(op.getId(), op.getRequestId(), StartOutcome.ALREADY_SUCCEEDED,
                    storedResult(op), op.isApplied());
            case FAILED -> {
                op.setStatus(ShippingOperationStatus.PENDING);
                op.setAttemptCount(op.getAttemptCount() + 1);
                op.setRequestId(newRequestId());
                op.setRequestedAt(now);
                op.setCompletedAt(null);
                op.setHttpStatus(null);
                op.setErrorCode(null);
                op.setErrorMessage(null);
                op.setProviderCode(request.providerCode());
                repository.saveAndFlush(op);
                yield new Start(op.getId(), op.getRequestId(), StartOutcome.STARTED, null, false);
            }
            case PENDING -> op.getRequestedAt().isAfter(now.minus(IN_PROGRESS_WINDOW))
                    ? new Start(op.getId(), op.getRequestId(), StartOutcome.IN_PROGRESS, null, false)
                    : new Start(op.getId(), op.getRequestId(), StartOutcome.IN_DOUBT, null, false);
            case UNKNOWN -> new Start(op.getId(), op.getRequestId(), StartOutcome.IN_DOUBT, null, false);
        };
    }

    private ShippingApiOperation newOperation(Request request, Instant now) {
        ShippingApiOperation op = new ShippingApiOperation();
        op.setProviderCode(request.providerCode());
        op.setOperationType(request.type());
        op.setOrderId(request.orderId());
        op.setShipmentId(request.shipmentId());
        op.setReturnRequestId(request.returnRequestId());
        op.setIdempotencyKey(request.idempotencyKey());
        op.setRequestId(newRequestId());
        op.setAttemptCount(1);
        op.setRequestedAt(now);
        return op;
    }

    private void update(Long operationId, java.util.function.Consumer<ShippingApiOperation> change) {
        newTx.executeWithoutResult(tx -> {
            ShippingApiOperation op = repository.findById(operationId).orElseThrow();
            change.accept(op);
            repository.save(op);
        });
    }

    /** Only non-personal fields: invoice number, vendor reference, whether it was automatic, a short message. */
    private String summarize(ProviderActionResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        if (result.trackingNumber() != null) {
            node.put("trackingNumber", result.trackingNumber());
        }
        if (result.externalReference() != null) {
            node.put("externalReference", truncate(result.externalReference(), 100));
        }
        node.put("automatic", result.automatic());
        if (result.message() != null) {
            node.put("message", truncate(result.message(), 200));
        }
        return truncate(node.toString(), 1000);
    }

    private ShippingApiOperationResponse toResponse(ShippingApiOperation op) {
        boolean attention = op.getStatus() == ShippingOperationStatus.UNKNOWN
                || (op.getStatus() == ShippingOperationStatus.PENDING
                && op.getRequestedAt().isBefore(clock.instant().minus(IN_PROGRESS_WINDOW)))
                || (op.getStatus() == ShippingOperationStatus.SUCCEEDED && op.getIdempotencyKey() != null
                && !op.isApplied());
        return new ShippingApiOperationResponse(
                op.getId(),
                op.getProviderCode(),
                op.getOperationType(),
                op.getOrderId(),
                op.getShipmentId(),
                op.getReturnRequestId(),
                op.getIdempotencyKey(),
                op.getRequestId(),
                op.getExternalReference(),
                op.getStatus(),
                op.getHttpStatus(),
                op.getErrorCode(),
                op.getErrorMessage(),
                op.getAttemptCount(),
                op.getRequestedAt(),
                op.getCompletedAt(),
                op.getAppliedAt(),
                attention);
    }

    private static ShippingOperationStatus parseStatus(String value) {
        try {
            return ShippingOperationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "알 수 없는 작업 상태입니다.");
        }
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
