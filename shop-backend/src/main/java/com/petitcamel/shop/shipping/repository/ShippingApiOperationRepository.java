package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShippingApiOperation;
import com.petitcamel.shop.shipping.domain.ShippingOperationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface ShippingApiOperationRepository extends JpaRepository<ShippingApiOperation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ShippingApiOperation o WHERE o.idempotencyKey = :key")
    Optional<ShippingApiOperation> findForUpdateByIdempotencyKey(@Param("key") String idempotencyKey);

    Optional<ShippingApiOperation> findByIdempotencyKey(String idempotencyKey);

    Page<ShippingApiOperation> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<ShippingApiOperation> findByStatusInOrderByRequestedAtDesc(
            Collection<ShippingOperationStatus> statuses, Pageable pageable);
}
