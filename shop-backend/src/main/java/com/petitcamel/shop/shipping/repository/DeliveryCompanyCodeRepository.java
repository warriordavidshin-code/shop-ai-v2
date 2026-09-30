package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.DeliveryCompanyCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryCompanyCodeRepository extends JpaRepository<DeliveryCompanyCode, DeliveryCompanyCode.Key> {

    Optional<DeliveryCompanyCode> findByCompanyCodeAndProvider(String companyCode, String provider);

    List<DeliveryCompanyCode> findByProvider(String provider);
}
