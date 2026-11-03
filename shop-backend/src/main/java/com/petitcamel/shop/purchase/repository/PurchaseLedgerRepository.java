package com.petitcamel.shop.purchase.repository;

import com.petitcamel.shop.purchase.domain.PurchaseLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseLedgerRepository extends JpaRepository<PurchaseLedger, Long>,
        JpaSpecificationExecutor<PurchaseLedger> {

    Optional<PurchaseLedger> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            select l from PurchaseLedger l
            where l.deletedAt is null and l.purchaseDate = :date
              and lower(trim(l.supplierName)) = lower(trim(:supplier))
              and l.purchaseAmount = :amount
            """)
    List<PurchaseLedger> findSameSupplierDateAmount(
            @Param("supplier") String supplier, @Param("date") LocalDate date, @Param("amount") Long amount);

    @Query("""
            select l from PurchaseLedger l
            where l.deletedAt is null and l.transferReference = :reference
              and (l.paymentAmount = :amount or l.purchaseAmount = :amount)
            """)
    List<PurchaseLedger> findSameTransferAndAmount(@Param("reference") String reference, @Param("amount") Long amount);

    @Query("select coalesce(max(l.purchaseDate), current_date) from PurchaseLedger l where l.deletedAt is null")
    LocalDate latestPurchaseDate();
}
