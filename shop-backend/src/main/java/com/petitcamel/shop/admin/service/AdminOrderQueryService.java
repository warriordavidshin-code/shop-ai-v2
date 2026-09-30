package com.petitcamel.shop.admin.service;

import com.petitcamel.shop.admin.dto.AdminOrderDetailResponse;
import com.petitcamel.shop.admin.dto.AdminOrderSummaryResponse;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.Member;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.domain.OrderItem;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.order.repository.OrderItemRepository;
import com.petitcamel.shop.order.service.OrderCancelRequestService;
import com.petitcamel.shop.order.service.OrderService;
import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import com.petitcamel.shop.shipping.domain.ReturnRequest;
import com.petitcamel.shop.shipping.domain.Shipment;
import com.petitcamel.shop.shipping.domain.ShipmentType;
import com.petitcamel.shop.shipping.repository.ReturnRequestRepository;
import com.petitcamel.shop.shipping.repository.ShipmentRepository;
import com.petitcamel.shop.shipping.service.DeliveryCompanyService;
import com.petitcamel.shop.shipping.service.ShipmentViewAssembler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin order list / detail with shipment and return state joined in. */
@Service
public class AdminOrderQueryService {

    /** Dashboard card / list tab filters. */
    public enum View {
        ALL, TODAY, READY, SHIPPING, DELIVERED, RETURN, CANCEL
    }

    static final Set<OrderStatus> READY_STATUSES = EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING);
    static final Set<OrderStatus> RETURN_STATUSES = EnumSet.of(OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED);
    static final Set<OrderStatus> CANCEL_STATUSES = EnumSet.of(OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED);

    private final OrderEntityRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MemberRepository memberRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ShipmentViewAssembler assembler;
    private final OrderService orderService;
    private final Clock clock;

    public AdminOrderQueryService(
            OrderEntityRepository orderRepository,
            OrderItemRepository orderItemRepository,
            MemberRepository memberRepository,
            ShipmentRepository shipmentRepository,
            ReturnRequestRepository returnRequestRepository,
            ShipmentViewAssembler assembler,
            OrderService orderService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.memberRepository = memberRepository;
        this.shipmentRepository = shipmentRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.assembler = assembler;
        this.orderService = orderService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> list(int page, int size, OrderStatus status, String view, String keyword) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100));
        Page<OrderEntity> orders;
        if (keyword != null && !keyword.isBlank()) {
            orders = orderRepository.findByOrderNoContainingOrderByOrderedAtDesc(keyword.trim(), pageable);
        } else if (status != null) {
            orders = orderRepository.findByOrderStatusOrderByOrderedAtDesc(status, pageable);
        } else {
            orders = switch (parseView(view)) {
                case ALL -> orderRepository.findAllByOrderByOrderedAtDesc(pageable);
                case TODAY -> orderRepository.findByOrderedAtGreaterThanEqualOrderByOrderedAtDesc(startOfToday(), pageable);
                case READY -> orderRepository.findByOrderStatusInOrderByOrderedAtDesc(READY_STATUSES, pageable);
                case SHIPPING -> orderRepository.findByOrderStatusOrderByOrderedAtDesc(OrderStatus.SHIPPED, pageable);
                case DELIVERED -> orderRepository.findByOrderStatusOrderByOrderedAtDesc(OrderStatus.DELIVERED, pageable);
                case RETURN -> orderRepository.findByOrderStatusInOrderByOrderedAtDesc(RETURN_STATUSES, pageable);
                case CANCEL -> orderRepository.findByOrderStatusInOrderByOrderedAtDesc(CANCEL_STATUSES, pageable);
            };
        }

        List<OrderEntity> content = orders.getContent();
        List<Long> orderIds = content.stream().map(OrderEntity::getOrderId).toList();
        Map<Long, List<OrderItem>> items = orderIds.isEmpty() ? Map.of()
                : orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, Member> members = memberRepository.findAllById(
                        content.stream().map(OrderEntity::getMemberId).distinct().toList()).stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        Map<Long, Shipment> deliveries = orderIds.isEmpty() ? Map.of()
                : shipmentRepository.findByOrderIdInAndShipmentType(orderIds, ShipmentType.DELIVERY).stream()
                .collect(Collectors.toMap(Shipment::getOrderId, Function.identity(), (a, b) -> a));
        Map<Long, ReturnRequest> returns = orderIds.isEmpty() ? Map.of()
                : returnRequestRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(ReturnRequest::getOrderId, Function.identity(),
                        (a, b) -> a.getRequestedAt().isAfter(b.getRequestedAt()) ? a : b));
        Map<String, DeliveryCompany> companies = assembler.companies();

        List<AdminOrderSummaryResponse> rows = content.stream()
                .map(order -> toSummary(order, items.getOrDefault(order.getOrderId(), List.of()),
                        members.get(order.getMemberId()), deliveries.get(order.getOrderId()),
                        returns.get(order.getOrderId()), companies))
                .toList();
        return PageResponse.of(rows, orders.getNumber(), orders.getSize(), orders.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse detail(String orderNo) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
        Member member = memberRepository.findById(order.getMemberId()).orElse(null);
        Map<String, DeliveryCompany> companies = assembler.companies();
        Shipment delivery = shipmentRepository.findByOrderIdAndShipmentType(order.getOrderId(), ShipmentType.DELIVERY)
                .orElse(null);
        Shipment returnShipment = shipmentRepository.findByOrderIdAndShipmentType(order.getOrderId(), ShipmentType.RETURN)
                .orElse(null);
        ReturnRequest returnRequest = returnRequestRepository.findFirstByOrderIdOrderByRequestedAtDesc(order.getOrderId())
                .orElse(null);
        return new AdminOrderDetailResponse(
                orderService.getOrderForAdmin(orderNo),
                member == null ? null : member.getLoginId(),
                member == null ? null : member.getName(),
                assembler.toView(delivery, true, companies),
                assembler.toView(returnShipment, true, companies),
                assembler.toReturnResponse(returnRequest, companies));
    }

    public Instant startOfToday() {
        return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
    }

    private static View parseView(String view) {
        if (view == null || view.isBlank()) {
            return View.ALL;
        }
        try {
            return View.valueOf(view.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "알 수 없는 조회 조건입니다: " + view);
        }
    }

    private static AdminOrderSummaryResponse toSummary(
            OrderEntity order,
            List<OrderItem> items,
            Member member,
            Shipment delivery,
            ReturnRequest returnRequest,
            Map<String, DeliveryCompany> companies) {
        String pickupStatus = null;
        if (delivery != null && delivery.getPickupRequestedAt() != null) {
            pickupStatus = delivery.getPickedUpAt() != null ? "집하완료" : "수거요청";
        }
        return new AdminOrderSummaryResponse(
                order.getOrderId(),
                order.getOrderNo(),
                order.getMemberId(),
                member == null ? null : member.getLoginId(),
                member == null ? null : member.getName(),
                order.getReceiverName(),
                OrderCancelRequestService.itemSummary(items),
                order.getOrderStatus(),
                order.getPaymentAmount(),
                order.getOrderedAt(),
                items.size(),
                delivery == null ? null : delivery.getShipmentStatus().name(),
                delivery == null ? null : delivery.getShipmentStatus().getLabel(),
                delivery == null ? null : delivery.getDeliveryCompany(),
                delivery == null ? null : DeliveryCompanyService.companyName(companies, delivery.getDeliveryCompany()),
                delivery == null ? null : delivery.getTrackingNumber(),
                delivery == null ? null
                        : DeliveryCompanyService.trackingUrl(companies, delivery.getDeliveryCompany(), delivery.getTrackingNumber()),
                pickupStatus,
                returnRequest == null ? null : returnRequest.getReturnStatus().name(),
                returnRequest == null ? null : returnRequest.getReturnStatus().getLabel());
    }
}
