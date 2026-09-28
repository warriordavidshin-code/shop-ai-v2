package com.petitcamel.shop.order.service;

import com.petitcamel.shop.admin.dto.AdminCancelRequestResponse;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.member.repository.MemberRepository;
import com.petitcamel.shop.order.domain.CancelRequestStatus;
import com.petitcamel.shop.order.domain.OrderCancelRequest;
import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.domain.OrderStatus;
import com.petitcamel.shop.order.repository.OrderCancelRequestRepository;
import com.petitcamel.shop.order.repository.OrderEntityRepository;
import com.petitcamel.shop.order.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

    @Mock
    OrderService orderService;
    @Mock
    OrderCancelRequestRepository cancelRequestRepository;
    @Mock
    OrderEntityRepository orderEntityRepository;
    @Mock
    OrderItemRepository orderItemRepository;
    @Mock
    MemberRepository memberRepository;
    @Mock
    AuditLogService auditLogService;

    OrderCancelRequestService service;

    @BeforeEach
    void setUp() {
        service = new OrderCancelRequestService(orderService, cancelRequestRepository, orderEntityRepository,
                orderItemRepository, memberRepository, auditLogService, CLOCK);
    }

    @Test
    void paidOrderMovesToCancelRequestedAndRemembersPreviousStatus() {
        OrderEntity order = order(OrderStatus.PAID);
        when(orderService.requireOrderByNo("ORD-1")).thenReturn(order);

        service.requestCancel(7L, "ORD-1", "  단순 변심  ");

        ArgumentCaptor<OrderCancelRequest> captor = ArgumentCaptor.forClass(OrderCancelRequest.class);
        verify(cancelRequestRepository).save(captor.capture());
        OrderCancelRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CancelRequestStatus.REQUESTED);
        assertThat(saved.getPreviousOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getReason()).isEqualTo("단순 변심");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        verify(orderService).requireOwner(order, 7L);
    }

    @Test
    void unpaidOrderMustBeCancelledDirectly() {
        when(orderService.requireOrderByNo("ORD-1")).thenReturn(order(OrderStatus.PAYMENT_PENDING));

        assertThatThrownBy(() -> service.requestCancel(7L, "ORD-1", "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(cancelRequestRepository, never()).save(any());
    }

    @Test
    void duplicateRequestIsConflict() {
        when(orderService.requireOrderByNo("ORD-1")).thenReturn(order(OrderStatus.CANCEL_REQUESTED));

        assertThatThrownBy(() -> service.requestCancel(7L, "ORD-1", "reason"))
                .extracting("code")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void shippedOrderCannotBeCancelled() {
        when(orderService.requireOrderByNo("ORD-1")).thenReturn(order(OrderStatus.SHIPPED));

        assertThatThrownBy(() -> service.requestCancel(7L, "ORD-1", "reason"))
                .extracting("code")
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void approveCancelsFromPreviousStatusAndAudits() {
        OrderEntity order = order(OrderStatus.CANCEL_REQUESTED);
        OrderCancelRequest request = openRequest(OrderStatus.PREPARING);
        when(cancelRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(orderEntityRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of());

        AdminCancelRequestResponse response = service.approve(3L, 99L);

        verify(orderService).applyCancellation(order, OrderStatus.PREPARING, 99L, NOW);
        assertThat(request.getStatus()).isEqualTo(CancelRequestStatus.APPROVED);
        assertThat(request.getProcessedBy()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(CancelRequestStatus.APPROVED);
        verify(auditLogService).record(eq(99L), eq("ORDER_CANCEL_APPROVE"), eq("ORDER"), eq("ORD-1"), anyString());
    }

    @Test
    void rejectRestoresPreviousOrderStatus() {
        OrderEntity order = order(OrderStatus.CANCEL_REQUESTED);
        OrderCancelRequest request = openRequest(OrderStatus.PAID);
        when(cancelRequestRepository.findById(3L)).thenReturn(Optional.of(request));
        when(orderEntityRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of());

        service.reject(3L, 99L, " 이미 출고 준비 완료 ");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(request.getStatus()).isEqualTo(CancelRequestStatus.REJECTED);
        assertThat(request.getRejectReason()).isEqualTo("이미 출고 준비 완료");
        verify(orderService, never()).applyCancellation(any(), any(), any(), any());
    }

    @Test
    void processedRequestCannotBeApprovedAgain() {
        OrderCancelRequest request = openRequest(OrderStatus.PAID);
        request.setStatus(CancelRequestStatus.APPROVED);
        when(cancelRequestRepository.findById(3L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(3L, 99L))
                .extracting("code")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    private static OrderEntity order(OrderStatus status) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(10L);
        order.setOrderNo("ORD-1");
        order.setMemberId(7L);
        order.setOrderStatus(status);
        order.setPaymentAmount(new BigDecimal("52000"));
        return order;
    }

    private static OrderCancelRequest openRequest(OrderStatus previous) {
        OrderCancelRequest request = new OrderCancelRequest();
        request.setCancelRequestId(3L);
        request.setOrderId(10L);
        request.setMemberId(7L);
        request.setStatus(CancelRequestStatus.REQUESTED);
        request.setReason("단순 변심");
        request.setPreviousOrderStatus(previous);
        request.setRequestedAt(NOW.minusSeconds(3600));
        return request;
    }
}
