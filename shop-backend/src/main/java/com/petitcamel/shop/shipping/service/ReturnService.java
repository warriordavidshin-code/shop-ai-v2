package com.petitcamel.shop.shipping.service;

import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.inventory.domain.Inventory;
import com.petitcamel.shop.inventory.domain.InventoryMovement;
import com.petitcamel.shop.inventory.domain.MovementType;
import com.petitcamel.shop.inventory.repository.InventoryMovementRepository;
import com.petitcamel.shop.inventory.repository.InventoryRepository;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.domain.OrderItem;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.event.OrderStatusChangedEvent;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.order.repository.OrderItemRepository;
import com.petitcamel.shop.order.service.OrderCancelRequestService;
import com.petitcamel.shop.payment.domain.Payment;
import com.petitcamel.shop.payment.domain.PaymentStatus;
import com.petitcamel.shop.payment.repository.PaymentRepository;
import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import com.petitcamel.shop.shipping.domain.ReturnReason;
import com.petitcamel.shop.shipping.domain.ReturnRequest;
import com.petitcamel.shop.shipping.domain.ReturnStatus;
import com.petitcamel.shop.shipping.domain.Shipment;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import com.petitcamel.shop.shipping.dto.AdminReturnResponse;
import com.petitcamel.shop.shipping.dto.CustomerReturnInfo;
import com.petitcamel.shop.shipping.dto.ReturnCreateRequest;
import com.petitcamel.shop.shipping.dto.ReturnRequestResponse;
import com.petitcamel.shop.shipping.provider.ProviderActionResult;
import com.petitcamel.shop.shipping.provider.ShipmentCommand;
import com.petitcamel.shop.shipping.repository.ReturnRequestRepository;
import com.petitcamel.shop.shipping.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Return business flow: 반품신청 → 반품승인 → 반품수거요청 → 반품배송중 → 반품입고 → 반품완료(환불).
 * The physical pickup is the request's RETURN {@link Shipment}; this service only owns the business status.
 * Returns cover the whole order. The refund marks the payment cancelled with the refunded amount; the actual PG
 * refund call is added when a real payment gateway is integrated.
 */
@Service
public class ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnService.class);

    /** Statuses an admin can set with the generic status action (other steps have dedicated actions). */
    static final Set<ReturnStatus> MANUAL_TARGETS = EnumSet.of(ReturnStatus.IN_PROGRESS, ReturnStatus.RECEIVED);

    private static final Set<ReturnStatus> REJECTABLE =
            EnumSet.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED, ReturnStatus.PICKUP_REQUESTED);

    private final ReturnRequestRepository returnRequestRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderEntityRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final MemberRepository memberRepository;
    private final ShipmentService shipmentService;
    private final ShippingFeeService shippingFeeService;
    private final DeliveryCompanyService deliveryCompanyService;
    private final ShippingOperationService operationService;
    private final ShipmentViewAssembler assembler;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReturnService(
            ReturnRequestRepository returnRequestRepository,
            ShipmentRepository shipmentRepository,
            OrderEntityRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository inventoryMovementRepository,
            MemberRepository memberRepository,
            ShipmentService shipmentService,
            ShippingFeeService shippingFeeService,
            DeliveryCompanyService deliveryCompanyService,
            ShippingOperationService operationService,
            ShipmentViewAssembler assembler,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.returnRequestRepository = returnRequestRepository;
        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.memberRepository = memberRepository;
        this.shipmentService = shipmentService;
        this.shippingFeeService = shippingFeeService;
        this.deliveryCompanyService = deliveryCompanyService;
        this.operationService = operationService;
        this.assembler = assembler;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ customer

    @Transactional(readOnly = true)
    public CustomerReturnInfo getReturnInfo(Long memberId, Long orderId) {
        OrderEntity order = requireOwnOrder(memberId, orderId);
        ReturnRequest latest = returnRequestRepository.findFirstByOrderIdOrderByRequestedAtDesc(orderId).orElse(null);
        boolean canRequest = order.getOrderStatus() == OrderStatus.DELIVERED
                && !returnRequestRepository.existsByOrderIdAndStatusNotIn(orderId, ReturnStatus.WITHDRAWN);
        boolean canCancel = latest != null && ReturnStatus.CUSTOMER_CANCELLABLE.contains(latest.getStatus());
        List<CustomerReturnInfo.ReasonOption> reasons = Arrays.stream(ReturnReason.values())
                .map(r -> new CustomerReturnInfo.ReasonOption(r.name(), r.getLabel(), r.isFreeReturn()))
                .toList();
        return new CustomerReturnInfo(
                toResponse(latest),
                canRequest,
                canCancel,
                shippingFeeService.returnShippingFee(),
                reasons);
    }

    @Transactional
    public ReturnRequestResponse requestReturn(Long memberId, Long orderId, ReturnCreateRequest request) {
        OrderEntity order = requireOwnOrder(memberId, orderId);
        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "배송완료된 주문만 반품을 신청할 수 있습니다.");
        }
        if (returnRequestRepository.existsByOrderIdAndStatusNotIn(orderId, ReturnStatus.WITHDRAWN)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 반품이 신청된 주문입니다.");
        }
        Instant now = clock.instant();
        ReturnReason reason = request.returnReason();
        long fee = reason.isFreeReturn() ? 0 : shippingFeeService.returnShippingFee();
        long refund = Math.max(0, toWon(order.getPaymentAmount()) - fee);

        ReturnRequest entity = new ReturnRequest();
        entity.setOrderId(orderId);
        entity.setReasonCode(reason);
        entity.setReasonText(trimToNull(request.returnMemo()));
        entity.setCustomerMemo(trimToNull(request.customerMemo()));
        entity.setStatus(ReturnStatus.REQUESTED);
        entity.setFreeReturn(reason.isFreeReturn());
        entity.setReturnShippingFee(fee);
        entity.setRefundAmount(refund);
        entity.setRequestedAt(now);
        ReturnRequest saved;
        try {
            saved = returnRequestRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 반품이 신청된 주문입니다.");
        }
        Shipment shipment = shipmentService.createReturnShipment(saved, new ShipmentCommand.Contact(
                request.pickupName().trim(),
                request.pickupPhone().trim(),
                request.pickupPostcode().trim(),
                request.pickupAddress().trim(),
                trimToNull(request.pickupAddressDetail())), now);
        changeOrderStatus(order, OrderStatus.RETURN_REQUESTED, memberId, now);

        auditLogService.record(memberId, "RETURN_REQUEST", "ORDER", order.getOrderNo(),
                "returnRequestId=" + saved.getReturnRequestId() + ", reason=" + reason);
        log.info("[SHIPPING] return requested orderId={} returnRequestId={} reason={}",
                orderId, saved.getReturnRequestId(), reason);
        return assembler.toReturnResponse(saved, shipment, assembler.refs());
    }

    /** Customer withdraws a return before the pickup is booked. */
    @Transactional
    public ReturnRequestResponse cancelByCustomer(Long memberId, Long orderId) {
        OrderEntity order = requireOwnOrder(memberId, orderId);
        ReturnRequest request = returnRequestRepository.findFirstByOrderIdOrderByRequestedAtDesc(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "반품 신청 내역이 없습니다."));
        requireStatus(request, ReturnStatus.CUSTOMER_CANCELLABLE, "수거가 요청된 반품은 직접 철회할 수 없습니다. 고객센터로 문의해 주세요.");
        Instant now = clock.instant();
        request.setStatus(ReturnStatus.CANCELLED);
        request.setCancelledAt(now);
        save(request);
        Shipment shipment = shipmentRepository.findByReturnRequestId(request.getReturnRequestId()).orElse(null);
        shipmentService.cancelReturnShipment(shipment, now, "고객이 반품을 철회했습니다.", memberId);
        if (order.getOrderStatus() == OrderStatus.RETURN_REQUESTED) {
            changeOrderStatus(order, OrderStatus.DELIVERED, memberId, now);
        }
        audit(memberId, "RETURN_CANCEL", request, null);
        return assembler.toReturnResponse(request, shipment, assembler.refs());
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<AdminReturnResponse> list(String statusFilter, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100));
        Page<ReturnRequest> result;
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            result = returnRequestRepository.findAllByOrderByRequestedAtDesc(pageable);
        } else if ("OPEN".equalsIgnoreCase(statusFilter)) {
            result = returnRequestRepository.findByStatusInOrderByRequestedAtDesc(ReturnStatus.OPEN, pageable);
        } else {
            ReturnStatus status = parseStatus(statusFilter);
            result = returnRequestRepository.findByStatusInOrderByRequestedAtDesc(List.of(status), pageable);
        }
        List<ReturnRequest> requests = result.getContent();
        List<Long> orderIds = requests.stream().map(ReturnRequest::getOrderId).distinct().toList();
        List<Long> requestIds = requests.stream().map(ReturnRequest::getReturnRequestId).toList();
        Map<Long, OrderEntity> orders = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(OrderEntity::getOrderId, Function.identity()));
        Map<Long, List<OrderItem>> items = orderIds.isEmpty() ? Map.of()
                : orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<Long> memberIds = orders.values().stream().map(OrderEntity::getMemberId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        Map<Long, Shipment> shipments = requestIds.isEmpty() ? Map.of()
                : shipmentRepository.findByReturnRequestIdIn(requestIds).stream()
                .collect(Collectors.toMap(Shipment::getReturnRequestId, Function.identity(), (a, b) -> a));
        ShipmentViewAssembler.Refs refs = assembler.refs();

        List<AdminReturnResponse> content = requests.stream()
                .map(r -> {
                    OrderEntity order = orders.get(r.getOrderId());
                    return toAdminResponse(r, order, items.getOrDefault(r.getOrderId(), List.of()),
                            order == null ? null : members.get(order.getMemberId()),
                            shipments.get(r.getReturnRequestId()), refs);
                })
                .toList();
        return PageResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminReturnResponse get(Long returnRequestId) {
        return toAdminResponse(requireReturn(returnRequestId));
    }

    @Transactional
    public AdminReturnResponse approve(Long returnRequestId, Long actorId) {
        ReturnRequest request = requireReturn(returnRequestId);
        requireStatus(request, EnumSet.of(ReturnStatus.REQUESTED), "반품신청 상태에서만 승인할 수 있습니다.");
        Instant now = clock.instant();
        request.setStatus(ReturnStatus.APPROVED);
        request.setApprovedAt(now);
        save(request);
        shipmentService.updateReturnShipment(requireShipment(request), null, null, null, null, now,
                "반품이 승인되었습니다.", actorId);
        audit(actorId, "RETURN_APPROVE", request, null);
        return toAdminResponse(request);
    }

    @Transactional
    public AdminReturnResponse reject(Long returnRequestId, String reason, Long actorId) {
        ReturnRequest request = requireReturn(returnRequestId);
        requireStatus(request, REJECTABLE, "수거가 시작된 반품은 거절할 수 없습니다.");
        Instant now = clock.instant();
        request.setStatus(ReturnStatus.REJECTED);
        request.setRejectReason(reason.trim());
        request.setRejectedAt(now);
        save(request);
        shipmentService.cancelReturnShipment(requireShipment(request), now, "반품이 거절되었습니다.", actorId);
        OrderEntity order = requireOrder(request.getOrderId());
        if (order.getOrderStatus() == OrderStatus.RETURN_REQUESTED) {
            changeOrderStatus(order, OrderStatus.DELIVERED, actorId, now);
        }
        audit(actorId, "RETURN_REJECT", request, null);
        return toAdminResponse(request);
    }

    /**
     * First half of "[반품수거 요청]": validates the return and collects the pickup address for the vendor.
     *
     * @param companyInput courier to use; blank keeps the courier already on the return shipment
     */
    @Transactional(readOnly = true)
    public ShipmentService.ExternalTarget prepareReturnPickup(Long returnRequestId, String companyInput) {
        ReturnRequest request = requireReturn(returnRequestId);
        requireStatus(request, EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.PICKUP_REQUESTED),
                "반품승인 후 수거요청을 할 수 있습니다.");
        Shipment shipment = requireShipment(request);
        OrderEntity order = requireOrder(request.getOrderId());
        Long companyId = shipment.getDeliveryCompanyId();
        String companyCode = null;
        if (!isBlank(companyInput)) {
            DeliveryCompany company = deliveryCompanyService.resolveEnabled(companyInput);
            companyId = company.getDeliveryCompanyId();
            companyCode = company.getCode();
        } else if (companyId != null) {
            companyCode = DeliveryCompanyService.companyCode(deliveryCompanyService.companiesById(), companyId);
        }
        return new ShipmentService.ExternalTarget(
                order.getOrderId(),
                order.getOrderNo(),
                order.getMemberId(),
                shipment.getShipmentId(),
                ShipmentType.RETURN,
                request.getReturnRequestId(),
                companyId,
                companyCode,
                new ShipmentCommand.Contact(shipment.getContactName(), shipment.getContactPhone(),
                        shipment.getPostalCode(), shipment.getAddress1(), shipment.getAddress2()),
                request.getCustomerMemo(),
                false);
    }

    /**
     * Second half of "[반품수거 요청]": saves the vendor result, moves the return to 반품수거요청 and marks the API
     * operation applied in the same transaction.
     *
     * @param trackingInput invoice typed by the admin (used when the vendor did not issue one)
     */
    @Transactional
    public AdminReturnResponse applyReturnPickup(
            ShipmentService.ExternalTarget target,
            Long operationId,
            Long providerId,
            String providerCode,
            ProviderActionResult result,
            String trackingInput,
            Long actorId) {
        ReturnRequest request = requireReturn(target.returnRequestId());
        requireStatus(request, EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.PICKUP_REQUESTED),
                "반품승인 후 수거요청을 할 수 있습니다.");
        String trackingNumber = !isBlank(result.trackingNumber()) ? result.trackingNumber()
                : isBlank(trackingInput) ? null : trackingInput;
        if (trackingNumber != null && target.deliveryCompanyId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "송장번호를 등록하려면 택배사를 선택해 주세요.");
        }
        Instant now = clock.instant();
        Shipment shipment = requireShipment(request);
        shipmentService.updateReturnShipment(shipment, ShipmentStatus.PICKUP_REQUESTED,
                target.deliveryCompanyId(), trackingNumber, providerId, now, "반품 수거를 요청했습니다.", actorId);
        if (request.getStatus() == ReturnStatus.APPROVED) {
            request.setStatus(ReturnStatus.PICKUP_REQUESTED);
            save(request);
        }
        operationService.markApplied(operationId);
        audit(actorId, "RETURN_PICKUP_REQUEST", request,
                "provider=" + providerCode + ", company=" + target.deliveryCompanyCode());
        return toAdminResponse(request);
    }

    /** Registers the return invoice so the return can be tracked like a delivery. */
    @Transactional
    public AdminReturnResponse registerTracking(Long returnRequestId, String companyInput, String trackingInput, Long actorId) {
        ReturnRequest request = requireReturn(returnRequestId);
        requireStatus(request, EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.PICKUP_REQUESTED, ReturnStatus.IN_PROGRESS),
                "현재 상태에서는 반품 송장을 등록할 수 없습니다.");
        if (isBlank(companyInput) || isBlank(trackingInput)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "택배사와 송장번호를 입력해 주세요.");
        }
        DeliveryCompany company = deliveryCompanyService.resolveEnabled(companyInput);
        Instant now = clock.instant();
        ShipmentStatus shipmentTarget = null;
        if (request.getStatus() == ReturnStatus.APPROVED) {
            request.setStatus(ReturnStatus.PICKUP_REQUESTED);
            save(request);
            shipmentTarget = ShipmentStatus.PICKUP_REQUESTED;
        }
        shipmentService.updateReturnShipment(requireShipment(request), shipmentTarget, company.getDeliveryCompanyId(),
                trackingInput, null, now, "반품 송장이 등록되었습니다.", actorId);
        audit(actorId, "RETURN_TRACKING_REGISTER", request, "company=" + company.getCode());
        return toAdminResponse(request);
    }

    @Transactional
    public AdminReturnResponse changeStatus(Long returnRequestId, ReturnStatus target, Long actorId) {
        if (!MANUAL_TARGETS.contains(target)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "직접 변경할 수 없는 반품 상태입니다.");
        }
        ReturnRequest request = requireReturn(returnRequestId);
        ReturnStatus current = request.getStatus();
        boolean allowed = (current == ReturnStatus.PICKUP_REQUESTED || current == ReturnStatus.IN_PROGRESS)
                && current.ordinal() < target.ordinal();
        if (!allowed) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    current.getLabel() + " 상태에서 " + target.getLabel() + "(으)로 변경할 수 없습니다.");
        }
        Instant now = clock.instant();
        request.setStatus(target);
        if (target == ReturnStatus.RECEIVED) {
            request.setReceivedAt(now);
        }
        save(request);
        ShipmentStatus shipmentTarget = target == ReturnStatus.RECEIVED ? ShipmentStatus.DELIVERED : ShipmentStatus.PICKED_UP;
        shipmentService.updateReturnShipment(requireShipment(request), shipmentTarget, null, null, null, now,
                "관리자가 '" + target.getLabel() + "' 상태로 변경했습니다.", actorId);
        audit(actorId, "RETURN_STATUS_UPDATE", request, "from=" + current + ", to=" + target);
        return toAdminResponse(request);
    }

    /** Refund after inspection: cancels the payment for the refund amount and optionally restocks. */
    @Transactional
    public AdminReturnResponse refund(Long returnRequestId, boolean restock, String adminMemo, Long actorId) {
        ReturnRequest request = requireReturn(returnRequestId);
        requireStatus(request, EnumSet.of(ReturnStatus.RECEIVED), "반품입고(상품 확인) 후 환불할 수 있습니다.");
        OrderEntity order = requireOrder(request.getOrderId());
        Instant now = clock.instant();

        Payment payment = paymentRepository.findByOrderId(order.getOrderId()).stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.APPROVED)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "환불할 결제 정보가 없습니다."));
        long refundAmount = request.getRefundAmount() == null ? 0 : request.getRefundAmount();
        payment.setPaymentStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(now);
        payment.setRefundedAmount(BigDecimal.valueOf(refundAmount));
        paymentRepository.save(payment);

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());
        for (OrderItem item : items) {
            if (restock) {
                restoreStock(item, order.getOrderNo(), actorId, now);
            }
            item.setStatus("RETURNED");
        }
        orderItemRepository.saveAll(items);

        request.setStatus(ReturnStatus.COMPLETED);
        request.setRestocked(restock);
        request.setCompletedAt(now);
        request.setAdminMemo(trimToNull(adminMemo));
        save(request);
        shipmentService.updateReturnShipment(requireShipment(request), ShipmentStatus.DELIVERED, null, null, null, now,
                null, actorId);
        changeOrderStatus(order, OrderStatus.RETURNED, actorId, now);

        audit(actorId, "RETURN_REFUND", request, "refundAmount=" + refundAmount + ", restock=" + restock);
        log.info("[SHIPPING] return refunded orderId={} returnRequestId={} refundAmount={} restock={}",
                order.getOrderId(), request.getReturnRequestId(), refundAmount, restock);
        return toAdminResponse(request);
    }

    // ------------------------------------------------------------------ helpers

    private void restoreStock(OrderItem item, String orderNo, Long actorId, Instant now) {
        Inventory inventory = inventoryRepository.findBySkuId(item.getSkuId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
        inventory.restoreStock(item.getQuantity());
        inventory.setUpdatedAt(now);
        try {
            inventoryRepository.saveAndFlush(inventory);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "재고가 다른 요청에 의해 변경되었습니다. 다시 시도해 주세요.");
        }
        InventoryMovement movement = new InventoryMovement();
        movement.setSkuId(item.getSkuId());
        movement.setMovementType(MovementType.RETURN);
        movement.setQuantity(item.getQuantity());
        movement.setReferenceType("ORDER");
        movement.setReferenceId(orderNo);
        movement.setReason("반품 입고(재고 복구)");
        movement.setActorMemberId(actorId);
        movement.setCreatedAt(now);
        inventoryMovementRepository.save(movement);
    }

    private void changeOrderStatus(OrderEntity order, OrderStatus next, Long actorId, Instant now) {
        OrderStatus previous = order.getOrderStatus();
        if (previous == next) {
            return;
        }
        order.setOrderStatus(next);
        order.setUpdatedAt(now);
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getOrderId(), order.getOrderNo(), previous, next, actorId));
    }

    private void save(ReturnRequest request) {
        try {
            returnRequestRepository.saveAndFlush(request);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 관리자가 먼저 처리했습니다. 새로고침 후 다시 시도해 주세요.");
        }
    }

    private void audit(Long actorId, String action, ReturnRequest request, String extra) {
        auditLogService.record(actorId, action, "RETURN_REQUEST", String.valueOf(request.getReturnRequestId()),
                "orderId=" + request.getOrderId() + ", status=" + request.getStatus()
                        + (extra == null ? "" : ", " + extra));
    }

    private ReturnRequestResponse toResponse(ReturnRequest request) {
        if (request == null) {
            return null;
        }
        Shipment shipment = shipmentRepository.findByReturnRequestId(request.getReturnRequestId()).orElse(null);
        return assembler.toReturnResponse(request, shipment, assembler.refs());
    }

    private AdminReturnResponse toAdminResponse(ReturnRequest request) {
        OrderEntity order = orderRepository.findById(request.getOrderId()).orElse(null);
        List<OrderItem> items = orderItemRepository.findByOrderId(request.getOrderId());
        Member member = order == null || order.getMemberId() == null ? null
                : memberRepository.findById(order.getMemberId()).orElse(null);
        Shipment shipment = shipmentRepository.findByReturnRequestId(request.getReturnRequestId()).orElse(null);
        return toAdminResponse(request, order, items, member, shipment, assembler.refs());
    }

    private AdminReturnResponse toAdminResponse(
            ReturnRequest request,
            OrderEntity order,
            List<OrderItem> items,
            Member member,
            Shipment shipment,
            ShipmentViewAssembler.Refs refs) {
        ReturnRequestResponse body = assembler.toReturnResponse(request, shipment, refs);
        return new AdminReturnResponse(
                body,
                order == null ? null : order.getOrderNo(),
                order == null ? null : order.getOrderStatus().name(),
                order == null ? null : order.getMemberId(),
                member == null ? null : member.getLoginId(),
                member == null ? null : member.getName(),
                OrderCancelRequestService.itemSummary(items),
                order == null ? null : order.getPaymentAmount(),
                shipment == null ? null : shipment.getStatus().name(),
                shipment == null ? null : shipment.getStatus().labelFor(shipment.getShipmentType()));
    }

    private Shipment requireShipment(ReturnRequest request) {
        return shipmentRepository.findByReturnRequestId(request.getReturnRequestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "반품 배송 정보가 없습니다."));
    }

    private ReturnRequest requireReturn(Long id) {
        return returnRequestRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "반품 요청을 찾을 수 없습니다."));
    }

    private OrderEntity requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    private OrderEntity requireOwnOrder(Long memberId, Long orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> Objects.equals(o.getMemberId(), memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    private static void requireStatus(ReturnRequest request, Set<ReturnStatus> allowed, String message) {
        if (!allowed.contains(request.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    message + " (현재: " + request.getStatus().getLabel() + ")");
        }
    }

    private static ReturnStatus parseStatus(String value) {
        try {
            return ReturnStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "알 수 없는 반품 상태입니다.");
        }
    }

    private static long toWon(BigDecimal amount) {
        return amount == null ? 0 : amount.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
