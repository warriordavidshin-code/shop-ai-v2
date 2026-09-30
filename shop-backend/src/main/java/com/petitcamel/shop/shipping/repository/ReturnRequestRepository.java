package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ReturnRequest;
import com.petitcamel.shop.shipping.domain.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    Optional<ReturnRequest> findFirstByOrderIdOrderByRequestedAtDesc(Long orderId);

    List<ReturnRequest> findByOrderIdIn(Collection<Long> orderIds);

    boolean existsByOrderIdAndReturnStatusNot(Long orderId, ReturnStatus status);

    Page<ReturnRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<ReturnRequest> findByReturnStatusInOrderByRequestedAtDesc(Collection<ReturnStatus> statuses, Pageable pageable);

    long countByReturnStatusIn(Collection<ReturnStatus> statuses);
}
