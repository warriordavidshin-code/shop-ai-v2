package com.petitcamel.shop.purchase.repository;

import com.petitcamel.shop.purchase.domain.PurchaseEvidence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseEvidenceRepository extends JpaRepository<PurchaseEvidence, Long> {

    List<PurchaseEvidence> findByPurchaseLedgerIdOrderByIdAsc(Long purchaseLedgerId);

    List<PurchaseEvidence> findByPurchaseLedgerIdIn(Collection<Long> ledgerIds);

    List<PurchaseEvidence> findByFileHash(String fileHash);

    Optional<PurchaseEvidence> findFirstByFileHashAndPurchaseLedgerIdIsNullOrderByIdDesc(String fileHash);

    Page<PurchaseEvidence> findAllByOrderByIdDesc(Pageable pageable);

    Page<PurchaseEvidence> findByPurchaseLedgerIdIsNullOrderByIdDesc(Pageable pageable);

    Page<PurchaseEvidence> findByPurchaseLedgerIdIsNotNullOrderByIdDesc(Pageable pageable);

    List<PurchaseEvidence> findByPurchaseLedgerIdIsNullAndCreatedAtBefore(Instant before);

    @Query("select count(e) from PurchaseEvidence e where e.purchaseLedgerId = :ledgerId")
    long countByLedger(@Param("ledgerId") Long ledgerId);
}
