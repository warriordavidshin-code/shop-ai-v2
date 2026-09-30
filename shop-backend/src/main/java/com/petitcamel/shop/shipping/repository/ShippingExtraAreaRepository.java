package com.petitcamel.shop.shipping.repository;

import com.petitcamel.shop.shipping.domain.ShippingExtraArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShippingExtraAreaRepository extends JpaRepository<ShippingExtraArea, Long> {

    List<ShippingExtraArea> findAllByOrderByAreaTypeAscPostcodeFromAsc();
}
