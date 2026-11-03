package com.petitcamel.shop.purchase.repository;

import com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus;
import com.petitcamel.shop.purchase.domain.PurchaseReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, Long> {

    Page<PurchaseReceipt> findAllByOrderByIdDesc(Pageable pageable);

    boolean existsByReceiptNo(String receiptNo);

    Page<PurchaseReceipt> findByStatusOrderByIdDesc(ReceiptStatus status, Pageable pageable);

    List<PurchaseReceipt> findByPurchaseLedgerIdOrderByIdAsc(Long purchaseLedgerId);

    /**
     * Claims a PENDING receipt for confirmation. Returns 1 for the single caller that wins; a repeated or
     * concurrent confirm gets 0 and must not touch stock.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PurchaseReceipt r
            set r.status = com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus.CONFIRMED,
                r.confirmedAt = :now, r.confirmedBy = :actorId, r.version = r.version + 1
            where r.id = :id and r.status = com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus.PENDING
            """)
    int claimForConfirm(@Param("id") Long id, @Param("now") Instant now, @Param("actorId") Long actorId);

    @Query("""
            select count(r) from PurchaseReceipt r
            where r.status = com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus.PENDING
            """)
    long countPending();
}
