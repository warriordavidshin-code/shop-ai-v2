package com.petitcamel.shop.purchase.repository;

import com.petitcamel.shop.purchase.domain.SupplierProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierProductMappingRepository extends JpaRepository<SupplierProductMapping, Long> {

    @Query("""
            select m from SupplierProductMapping m
            where m.supplierId = :supplierId and lower(m.supplierProductCode) = lower(:code)
            """)
    List<SupplierProductMapping> findByCode(@Param("supplierId") Long supplierId, @Param("code") String code);

    @Query("""
            select m from SupplierProductMapping m
            where m.supplierId = :supplierId and lower(trim(m.supplierProductName)) = lower(trim(:name))
            """)
    List<SupplierProductMapping> findByName(@Param("supplierId") Long supplierId, @Param("name") String name);

    List<SupplierProductMapping> findBySupplierIdOrderByUpdatedAtDesc(Long supplierId);

    List<SupplierProductMapping> findByProductId(Long productId);
}
