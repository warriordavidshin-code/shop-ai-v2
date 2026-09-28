package com.petitcamel.shop.order.repository;

import com.petitcamel.shop.order.domain.CancelRequestStatus;
import com.petitcamel.shop.order.domain.OrderCancelRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderCancelRequestRepository extends JpaRepository<OrderCancelRequest, Long> {

    Optional<OrderCancelRequest> findFirstByOrderIdOrderByRequestedAtDesc(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, CancelRequestStatus status);

    Page<OrderCancelRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<OrderCancelRequest> findByStatusOrderByRequestedAtDesc(CancelRequestStatus status, Pageable pageable);

    long countByStatus(CancelRequestStatus status);
}
