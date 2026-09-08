package com.petitcamel.shop.payment.repository;

import com.petitcamel.shop.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByProviderTransactionId(String providerTransactionId);
}
