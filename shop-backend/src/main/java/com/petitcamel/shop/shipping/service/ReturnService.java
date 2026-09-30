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
import com.petitcamel.shop.shipping.pickup.PickupRequest;
import com.petitcamel.shop.shipping.pickup.PickupResponse;
import com.petitcamel.shop.shipping.pickup.PickupService;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Return flow: 반품신청 → 반품승인 → 반품수거요청 → 기사방문수거 → 반품배송중 → 반품입고 → 환불처리.
 * Returns cover the whole order. The refund marks the payment cancelled with the refunded amount; the
 * actual PG refund call is added when a real payment gateway is integrated.
 */
@Service
public class ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnService.class);

    /** Statuses an admin can set with the generic status action (other steps have dedicated actions). */
    static final Set<ReturnStatus> MANUAL_TARGETS =
            EnumSet.of(ReturnStatus.PICKED_UP, ReturnStatus.IN_TRANSIT, ReturnStatus.RECEIVED);

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
    private final PickupService pickupService;
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
            PickupService pickupService,
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
        this.pickupService = pickupService;
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
                && !returnRequestRepository.existsByOrderIdAndReturnStatusNot(orderId, ReturnStatus.REJECTED);
        List<CustomerReturnInfo.ReasonOption> reasons = Arrays.stream(ReturnReason.values())
                .map(r -> new CustomerReturnInfo.ReasonOption(r.name(), r.getLabel(), r.isFreeReturn()))
                .toList();
        return new CustomerReturnInfo(
                assembler.toReturnResponse(latest, assembler.companies()),
                canRequest,
                shippingFeeService.returnShippingFee(),
                reasons);
    }

    @Transactional
    public ReturnRequestResponse requestReturn(Long memberId, Long orderId, ReturnCreateRequest request) {
        OrderEntity order = requireOwnOrder(memberId, orderId);
        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "배송완료된 주문만 반품을 신청할 수 있습니다.");
        }
        if (returnRequestRepository.existsByOrderIdAndReturnStatusNot(orderId, ReturnStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 반품이 신청된 주문입니다.");
        }
        Instant now = clock.instant();
        ReturnReason reason = request.returnReason();
        BigDecimal fee = reason.isFreeReturn() ? BigDecimal.ZERO : shippingFeeService.returnShippingFee();
        BigDecimal refund = order.getPaymentAmount().subtract(fee).max(BigDecimal.ZERO);

        ReturnRequest entity = new ReturnRequest();
        entity.setOrderId(orderId);
        entity.setMemberId(memberId);
        entity.setReason(reason);
        entity.setMemo(trimToNull(request.returnMemo()));
        entity.setReturnStatus(ReturnStatus.REQUESTED);
        entity.setPickupName(request.pickupName().trim());
        entity.setPickupPhone(request.pickupPhone().trim());
        entity.setPickupPostcode(request.pickupPostcode().trim());
        entity.setPickupAddress1(request.pickupAddress().trim());
        entity.setPickupAddress2(trimToNull(request.pickupAddressDetail()));
        entity.setFreeReturn(reason.isFreeReturn());
        entity.setReturnShippingFee(fee);
        entity.setRefundAmount(refund);
        entity.setRequestedAt(now);
        entity.setUpdatedAt(now);
        ReturnRequest saved;
        try {
            saved = returnRequestRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 반품이 신청된 주문입니다.");
        }
        shipmentService.createReturnShipment(saved, now);
        changeOrderStatus(order, OrderStatus.RETURN_REQUESTED, memberId, now);

        auditLogService.record(memberId, "RETURN_REQUEST", "ORDER", order.getOrderNo(),
                "returnRequestId=" + saved.getReturnRequestId() + ", reason=" + reason);
        log.info("[SHIPPING] return requested orderId={} returnRequestId={} reason={}",
                orderId, saved.getReturnRequestId(), reason);
        return assembler.toReturnResponse(saved, assembler.companies());
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<AdminReturnResponse> list(String statusFilter, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100));
        Page<ReturnRequest> result;
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            result = returnRequestRepository.findAllByOrderByRequestedAtDesc(pageable);
        } else if ("OPEN".equalsIgnoreCase(statusFilter)) {
            result = returnRequestRepository.findByReturnStatusInOrderByRequestedAtDesc(ReturnStatus.OPEN, pageable);
        } else {
            ReturnStatus status = parseStatus(statusFilter);
            result = returnRequestRepository.findByReturnStatusInOrderByRequestedAtDesc(List.of(status), pageable);
        }
        List<ReturnRequest> requests = result.getContent();
        List<Long> orderIds = requests.stream().map(ReturnRequest::getOrderId).distinct().toList();
        Map<Long, OrderEntity> orders = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(OrderEntity::getOrderId, Function.identity()));
        Map<Long, List<OrderItem>> items = orderIds.isEmpty() ? Map.of()
                : orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<Long> memberIds = requests.stream().map(ReturnRequest::getMemberId).distinct().toList();
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        Map<Long, Shipment> shipments = orderIds.isEmpty() ? Map.of()
                : shipmentRepository.findByOrderIdInAndShipmentType(orderIds, ShipmentType.RETURN).stream()
                .filter(s -> s.getReturnRequestId() != null)
                .collect(Collectors.toMap(Shipment::getReturnRequestId, Function.identity(), (a, b) -> a));
        Map<String, DeliveryCompany> companies = assembler.companies();

        List<AdminReturnResponse> content = requests.stream()
                .map(r -> toAdminResponse(r, orders.get(r.getOrderId()), items.getOrDefault(r.getOrderId(), List.of()),
                        members.get(r.getMemberId()), shipments.get(r.getReturnRequestId()), companies))
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
        request.setReturnStatus(ReturnStatus.APPROVED);
        request.setApprovedAt(now);
        save(request, actorId, now);
        shipmentService.updateReturnShipment(request, null, null, null, now, "반품이 승인되었습니다.", actorId);
        audit(actorId, "RETURN_APPROVE", request, null);
        return toAdminResponse(request);
    }

    @Transactional
    public AdminReturnResponse reject(Long returnRequestId, String reason, Long actorId) {
        ReturnRequest request = requireReturn(returnRequestId);
        requireStatus(request, REJECTABLE, "수거가 시작된 반품은 거절할 수 없습니다.");
        Instant now = clock.instant();
        request.setReturnStatus(ReturnStatus.REJECTED);
        request.setRejectReason(reason.trim());
        request.setRejectedAt(now);
        save(request, actorId, now);
        shipmentService.deleteReturnShipment(request.getReturnRequestId());
        OrderEntity order = requireOrder(request.getOrderId());
        if (order.getOrderStatus() == OrderStatus.RETURN_REQUESTED) {
            changeOrderStatus(order, OrderStatus.DELIVERED, actorId, now);
        }
        audit(actorId, "RETURN_REJECT", request, null);
        return toAdminResponse(request);
    }

    /** "[반품수거 요청]": asks the pickup service (manual for now) and moves the return to 반품수거요청. */
    @Transactional
    public AdminReturnResponse requestPickup(Long returnRequestId, String companyInput, String trackingInput, Long actorId) {
        ReturnRequest request = requireReturn(returnRequestId);
        requireStatus(request, EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.PICKUP_REQUESTED),
                "반품승인 후 수거요청을 할 수 있습니다.");
        String companyCode = isBlank(companyInput)
                ? request.getPickupDeliveryCompany()
                : deliveryCompanyService.resolveEnabled(companyInput).getCode();
        String trackingNumber = isBlank(trackingInput) ? null : ShipmentService.normalizeTrackingNumber(trackingInput);
        OrderEntity order = requireOrder(request.getOrderId());

        PickupResponse response = pickupService.requestPickup(new PickupRequest(
                order.getOrderId(), order.getOrderNo(), ShipmentType.RETURN, companyCode,
                request.getPickupName(), request.getPickupPhone(), request.getPickupPostcode(),
                request.getPickupAddress1(), request.getPickupAddress2(), request.getMemo()));
        if (!response.accepted()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    response.message() == null ? "반품 수거요청에 실패했습니다." : response.message());
        }
        if (trackingNumber == null && !isBlank(response.trackingNumber())) {
            trackingNumber = ShipmentService.normalizeTrackingNumber(response.trackingNumber());
        }

        Instant now = clock.instant();
        request.setReturnStatus(ReturnStatus.PICKUP_REQUESTED);
        if (request.getPickupRequestedAt() == null) {
            request.setPickupRequestedAt(now);
        }
        request.setPickupDeliveryCompany(companyCode);
        if (trackingNumber != null) {
            request.setPickupTrackingNumber(trackingNumber);
        }
        save(request, actorId, now);
        shipmentService.updateReturnShipment(request, ShipmentStatus.RETURN_PICKUP_REQUESTED,
                companyCode, request.getPickupTrackingNumber(), now, "반품 수거를 요청했습니다.", actorId);
        audit(actorId, "RETURN_PICKUP_REQUEST", request, "service=" + pickupService.name() + ", company=" + companyCode);
        return toAdminResponse(request);
    }

    /** Registers the return invoice so the return can be tracked like a delivery. */
    @Transactional
    public AdminReturnResponse registerTracking(Long returnRequestId, String companyInput, String trackingInput, Long actorId) {
        ReturnRequest request = requireReturn(returnRequestId);
        requireStatus(request, EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.PICKUP_REQUESTED,
                ReturnStatus.PICKED_UP, ReturnStatus.IN_TRANSIT), "현재 상태에서는 반품 송장을 등록할 수 없습니다.");
        if (isBlank(companyInput) || isBlank(trackingInput)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "택배사와 송장번호를 입력해 주세요.");
        }
        String companyCode = deliveryCompanyService.resolveEnabled(companyInput).getCode();
        String trackingNumber = ShipmentService.normalizeTrackingNumber(trackingInput);
        Instant now = clock.instant();
        request.setPickupDeliveryCompany(companyCode);
        request.setPickupTrackingNumber(trackingNumber);
        ShipmentStatus shipmentTarget = null;
        if (request.getReturnStatus() == ReturnStatus.APPROVED) {
            request.setReturnStatus(ReturnStatus.PICKUP_REQUESTED);
            request.setPickupRequestedAt(now);
            shipmentTarget = ShipmentStatus.RETURN_PICKUP_REQUESTED;
        }
        save(request, actorId, now);
        shipmentService.updateReturnShipment(request, shipmentTarget, companyCode, trackingNumber, now,
                "반품 송장이 등록되었습니다.", actorId);
        audit(actorId, "RETURN_TRACKING_REGISTER", request, "company=" + companyCode);
        return toAdminResponse(request);
    }

    @Transactional
    public AdminReturnResponse changeStatus(Long returnRequestId, ReturnStatus target, Long actorId) {
        if (!MANUAL_TARGETS.contains(target)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "직접 변경할 수 없는 반품 상태입니다.");
        }
        ReturnRequest request = requireReturn(returnRequestId);
        ReturnStatus current = request.getReturnStatus();
        if (!ReturnStatus.OPEN.contains(current) || current.ordinal() >= target.ordinal()
                || current.ordinal() < ReturnStatus.PICKUP_REQUESTED.ordinal()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    current.getLabel() + " 상태에서 " + target.getLabel() + "(으)로 변경할 수 없습니다.");
        }
        Instant now = clock.instant();
        request.setReturnStatus(target);
        if (request.getPickedUpAt() == null) {
            request.setPickedUpAt(now);
        }
        if (target == ReturnStatus.RECEIVED) {
            request.setReceivedAt(now);
        }
        save(request, actorId, now);
        ShipmentStatus shipmentTarget = target == ReturnStatus.RECEIVED
                ? ShipmentStatus.RETURN_COMPLETED
                : ShipmentStatus.RETURN_IN_TRANSIT;
        shipmentService.updateReturnShipment(request, shipmentTarget, null, null, now,
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
        payment.setPaymentStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(now);
        payment.setRefundedAmount(request.getRefundAmount());
        paymentRepository.save(payment);

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());
        for (OrderItem item : items) {
            if (restock) {
                restoreStock(item, order.getOrderNo(), actorId, now);
            }
            item.setStatus("RETURNED");
        }
        orderItemRepository.saveAll(items);

        request.setReturnStatus(ReturnStatus.REFUNDED);
        request.setRestocked(restock);
        request.setCompletedAt(now);
        request.setAdminMemo(trimToNull(adminMemo));
        save(request, actorId, now);
        shipmentService.updateReturnShipment(request, ShipmentStatus.RETURN_COMPLETED, null, null, now, null, actorId);
        changeOrderStatus(order, OrderStatus.RETURNED, actorId, now);

        audit(actorId, "RETURN_REFUND", request,
                "refundAmount=" + request.getRefundAmount() + ", restock=" + restock);
        log.info("[SHIPPING] return refunded orderId={} returnRequestId={} refundAmount={} restock={}",
                order.getOrderId(), request.getReturnRequestId(), request.getRefundAmount(), restock);
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

    private void save(ReturnRequest request, Long actorId, Instant now) {
        request.setUpdatedAt(now);
        if (actorId != null && !Objects.equals(actorId, request.getMemberId())) {
            request.setProcessedBy(actorId);
        }
        try {
            returnRequestRepository.saveAndFlush(request);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "다른 관리자가 먼저 처리했습니다. 새로고침 후 다시 시도해 주세요.");
        }
    }

    private void audit(Long actorId, String action, ReturnRequest request, String extra) {
        auditLogService.record(actorId, action, "RETURN_REQUEST", String.valueOf(request.getReturnRequestId()),
                "orderId=" + request.getOrderId() + ", status=" + request.getReturnStatus()
                        + (extra == null ? "" : ", " + extra));
    }

    private AdminReturnResponse toAdminResponse(ReturnRequest request) {
        OrderEntity order = orderRepository.findById(request.getOrderId()).orElse(null);
        List<OrderItem> items = orderItemRepository.findByOrderId(request.getOrderId());
        Member member = memberRepository.findById(request.getMemberId()).orElse(null);
        Shipment shipment = shipmentRepository.findByReturnRequestId(request.getReturnRequestId()).orElse(null);
        return toAdminResponse(request, order, items, member, shipment, assembler.companies());
    }

    private AdminReturnResponse toAdminResponse(
            ReturnRequest request,
            OrderEntity order,
            List<OrderItem> items,
            Member member,
            Shipment shipment,
            Map<String, DeliveryCompany> companies) {
        ReturnRequestResponse body = assembler.toReturnResponse(request, companies);
        return new AdminReturnResponse(
                body,
                order == null ? null : order.getOrderNo(),
                order == null ? null : order.getOrderStatus().name(),
                request.getMemberId(),
                member == null ? null : member.getLoginId(),
                member == null ? null : member.getName(),
                OrderCancelRequestService.itemSummary(items),
                order == null ? null : order.getPaymentAmount(),
                shipment == null ? null : shipment.getShipmentStatus().name(),
                shipment == null ? null : shipment.getShipmentStatus().getLabel());
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
        if (!allowed.contains(request.getReturnStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    message + " (현재: " + request.getReturnStatus().getLabel() + ")");
        }
    }

    private static ReturnStatus parseStatus(String value) {
        try {
            return ReturnStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "알 수 없는 반품 상태입니다.");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
