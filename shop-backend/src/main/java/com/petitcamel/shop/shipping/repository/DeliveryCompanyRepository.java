package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryCompanyRepository extends JpaRepository<DeliveryCompany, Long> {

    Optional<DeliveryCompany> findByCode(String code);

    List<DeliveryCompany> findAllByOrderBySortOrderAscCodeAsc();
}
