package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.DeliveryCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryCompanyRepository extends JpaRepository<DeliveryCompany, String> {

    List<DeliveryCompany> findAllByOrderBySortOrderAscCodeAsc();
}
