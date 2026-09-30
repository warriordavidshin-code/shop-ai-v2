package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShippingExtraArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShippingExtraAreaRepository extends JpaRepository<ShippingExtraArea, Long> {

    List<ShippingExtraArea> findAllByOrderByAreaTypeAscPostalCodeFromAsc();

    @Query("""
            SELECT a FROM ShippingExtraArea a
            WHERE a.enabled = true AND a.postalCodeFrom <= :postalCode AND a.postalCodeTo >= :postalCode
            """)
    List<ShippingExtraArea> findEnabledContaining(@Param("postalCode") String postalCode);
}
