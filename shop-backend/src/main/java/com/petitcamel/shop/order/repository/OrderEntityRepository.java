package com.petitcamel.shop.order.repository;

import com.petitcamel.shop.order.domain.OrderEntity;
import com.petitcamel.shop.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderEntityRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderNo(String orderNo);

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    List<OrderEntity> findByMemberIdOrderByOrderedAtDesc(Long memberId);

    Page<OrderEntity> findByMemberIdOrderByOrderedAtDesc(Long memberId, Pageable pageable);

    Page<OrderEntity> findAllByOrderByOrderedAtDesc(Pageable pageable);

    Page<OrderEntity> findByOrderStatusOrderByOrderedAtDesc(OrderStatus orderStatus, Pageable pageable);

    long countByOrderStatus(OrderStatus orderStatus);

    long countByOrderedAtAfter(Instant after);
}
