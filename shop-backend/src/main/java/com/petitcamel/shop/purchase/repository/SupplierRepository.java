package com.petitcamel.shop.purchase.repository;

import com.petitcamel.shop.purchase.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    @Query("select s from Supplier s where lower(trim(s.supplierName)) = lower(trim(:name))")
    Optional<Supplier> findByNameIgnoreCase(@Param("name") String name);

    Optional<Supplier> findFirstByBusinessNo(String businessNo);

    List<Supplier> findAllByOrderBySupplierNameAsc();
}
