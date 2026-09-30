package com.petitcamel.shop.order.service;

import com.petitcamel.shop.admin.dto.AdminCancelRequestResponse;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.order.domain.CancelRequestStatus;
import com.petitcamel.shop.order.domain.OrderCancelRequest;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.domain.OrderItem;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.dto.OrderResponse;
import com.petitcamel.shop.order.repository.OrderCancelRequestRepository;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.order.repository.OrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderCancelRequestService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelRequestService.class);

    private final OrderService orderService;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderEntityRepository orderEntityRepository;
    private final OrderItemRepository orderItemRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public OrderCancelRequestService(
            OrderService orderService,
            OrderCancelRequestRepository cancelRequestRepository,
            OrderEntityRepository orderEntityRepository,
            OrderItemRepository orderItemRepository,
            MemberRepository memberRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.orderService = orderService;
        this.cancelRequestRepository = cancelRequestRepository;
        this.orderEntityRepository = orderEntityRepository;
        this.orderItemRepository = orderItemRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse requestCancel(Long memberId, String orderNo, String reason) {
        OrderEntity order = orderService.requireOrderByNo(orderNo);
        orderService.requireOwner(order, memberId);

        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "결제 전 주문은 취소 요청 없이 바로 취소할 수 있습니다.");
        }
        if (status == OrderStatus.CANCEL_REQUESTED
                || cancelRequestRepository.existsByOrderIdAndStatus(order.getOrderId(), CancelRequestStatus.REQUESTED)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 취소 요청이 접수된 주문입니다.");
        }
        if (status != OrderStatus.PAID && status != OrderStatus.PREPARING) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "배송이 시작되었거나 취소된 주문은 취소 요청할 수 없습니다.");
        }

        Instant now = clock.instant();
        OrderCancelRequest request = new OrderCancelRequest();
        request.setOrderId(order.getOrderId());
        request.setMemberId(memberId);
        request.setStatus(CancelRequestStatus.REQUESTED);
        request.setReason(reason.trim());
        request.setPreviousOrderStatus(status);
        request.setRequestedAt(now);
        cancelRequestRepository.save(request);

        order.setOrderStatus(OrderStatus.CANCEL_REQUESTED);
        order.setUpdatedAt(now);
        orderEntityRepository.save(order);
        log.info("Order cancel requested memberId={} orderNo={} previousStatus={}", memberId, orderNo, status);

        return orderService.toOrderResponse(order, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCancelRequestResponse> listForAdmin(CancelRequestStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        Page<OrderCancelRequest> requests = status == null
                ? cancelRequestRepository.findAllByOrderByRequestedAtDesc(pageable)
                : cancelRequestRepository.findByStatusOrderByRequestedAtDesc(status, pageable);

        List<Long> orderIds = requests.getContent().stream().map(OrderCancelRequest::getOrderId).distinct().toList();
        List<Long> memberIds = requests.getContent().stream().map(OrderCancelRequest::getMemberId).distinct().toList();
        Map<Long, OrderEntity> orders = orderEntityRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(OrderEntity::getOrderId, Function.identity()));
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        Map<Long, List<OrderItem>> itemsByOrder = orderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<AdminCancelRequestResponse> content = requests.getContent().stream()
                .map(r -> toAdminResponse(
                        r,
                        orders.get(r.getOrderId()),
                        members.get(r.getMemberId()),
                        itemsByOrder.getOrDefault(r.getOrderId(), List.of())))
                .toList();
        return PageResponse.of(content, requests.getNumber(), requests.getSize(), requests.getTotalElements());
    }

    @Transactional
    public AdminCancelRequestResponse approve(Long cancelRequestId, Long adminMemberId) {
        OrderCancelRequest request = requireOpenRequest(cancelRequestId);
        OrderEntity order = requireOrder(request.getOrderId());
        if (order.getOrderStatus() != OrderStatus.CANCEL_REQUESTED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "취소 요청 상태의 주문이 아닙니다.");
        }

        Instant now = clock.instant();
        orderService.applyCancellation(order, request.getPreviousOrderStatus(), adminMemberId, now);

        request.setStatus(CancelRequestStatus.APPROVED);
        request.setProcessedBy(adminMemberId);
        request.setProcessedAt(now);
        cancelRequestRepository.save(request);

        auditLogService.record(adminMemberId, "ORDER_CANCEL_APPROVE", "ORDER", order.getOrderNo(),
                "cancelRequestId=" + cancelRequestId + ", from=" + request.getPreviousOrderStatus());
        return toAdminResponse(request, order, memberRepository.findById(request.getMemberId()).orElse(null),
                orderItemRepository.findByOrderId(order.getOrderId()));
    }

    @Transactional
    public AdminCancelRequestResponse reject(Long cancelRequestId, Long adminMemberId, String rejectReason) {
        OrderCancelRequest request = requireOpenRequest(cancelRequestId);
        OrderEntity order = requireOrder(request.getOrderId());

        Instant now = clock.instant();
        if (order.getOrderStatus() == OrderStatus.CANCEL_REQUESTED) {
            order.setOrderStatus(request.getPreviousOrderStatus());
            order.setUpdatedAt(now);
            orderEntityRepository.save(order);
        }

        request.setStatus(CancelRequestStatus.REJECTED);
        request.setRejectReason(rejectReason.trim());
        request.setProcessedBy(adminMemberId);
        request.setProcessedAt(now);
        cancelRequestRepository.save(request);

        auditLogService.record(adminMemberId, "ORDER_CANCEL_REJECT", "ORDER", order.getOrderNo(),
                "cancelRequestId=" + cancelRequestId + ", restored=" + request.getPreviousOrderStatus());
        return toAdminResponse(request, order, memberRepository.findById(request.getMemberId()).orElse(null),
                orderItemRepository.findByOrderId(order.getOrderId()));
    }

    private OrderCancelRequest requireOpenRequest(Long cancelRequestId) {
        OrderCancelRequest request = cancelRequestRepository.findById(cancelRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "취소 요청을 찾을 수 없습니다."));
        if (request.getStatus() != CancelRequestStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 취소 요청입니다.");
        }
        return request;
    }

    private OrderEntity requireOrder(Long orderId) {
        return orderEntityRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    private static AdminCancelRequestResponse toAdminResponse(
            OrderCancelRequest request,
            OrderEntity order,
            Member member,
            List<OrderItem> items) {
        return new AdminCancelRequestResponse(
                request.getCancelRequestId(),
                request.getOrderId(),
                order == null ? null : order.getOrderNo(),
                order == null ? null : order.getOrderStatus(),
                request.getMemberId(),
                member == null ? null : member.getLoginId(),
                member == null ? null : member.getName(),
                request.getStatus(),
                request.getReason(),
                request.getPreviousOrderStatus(),
                request.getRejectReason(),
                order == null ? null : order.getPaymentAmount(),
                itemSummary(items),
                request.getRequestedAt(),
                request.getProcessedAt());
    }

    public static String itemSummary(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return "-";
        }
        String first = items.get(0).getProductName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }
}
