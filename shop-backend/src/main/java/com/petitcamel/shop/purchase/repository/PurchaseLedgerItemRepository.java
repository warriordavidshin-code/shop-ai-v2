package com.petitcamel.shop.purchase.repository;

import com.petitcamel.shop.purchase.domain.PurchaseEnums.MatchStatus;
import com.petitcamel.shop.purchase.domain.PurchaseLedgerItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseLedgerItemRepository extends JpaRepository<PurchaseLedgerItem, Long> {

    List<PurchaseLedgerItem> findByPurchaseLedgerIdOrderByLineNoAsc(Long purchaseLedgerId);

    List<PurchaseLedgerItem> findByPurchaseLedgerIdIn(Collection<Long> ledgerIds);

    void deleteByPurchaseLedgerId(Long purchaseLedgerId);

    @Query("""
            select i from PurchaseLedgerItem i, PurchaseLedger l
            where i.purchaseLedgerId = l.id and l.deletedAt is null and i.matchStatus in :statuses
            order by l.purchaseDate desc, i.purchaseLedgerId desc, i.lineNo asc
            """)
    Page<PurchaseLedgerItem> findOpenByMatchStatus(@Param("statuses") Collection<MatchStatus> statuses,
                                                   Pageable pageable);

    @Query("""
            select i from PurchaseLedgerItem i, PurchaseLedger l
            where i.purchaseLedgerId = l.id and l.deletedAt is null and i.matchedSkuId = :skuId
            order by l.purchaseDate desc
            """)
    List<PurchaseLedgerItem> findBySku(@Param("skuId") Long skuId);
}
