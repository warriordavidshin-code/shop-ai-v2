package com.petitcamel.shop.purchase.repository;

import com.petitcamel.shop.purchase.domain.PurchaseReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseReceiptItemRepository extends JpaRepository<PurchaseReceiptItem, Long> {

    List<PurchaseReceiptItem> findByPurchaseReceiptIdOrderByIdAsc(Long purchaseReceiptId);

    List<PurchaseReceiptItem> findByPurchaseReceiptIdIn(Collection<Long> receiptIds);

    /** Quantity still expected on PENDING receipts, per SKU (incoming stock). */
    @Query("""
            select ri.skuId, coalesce(sum(ri.expectedQuantity), 0) from PurchaseReceiptItem ri, PurchaseReceipt r
            where ri.purchaseReceiptId = r.id
              and r.status = com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus.PENDING
            group by ri.skuId
            """)
    List<Object[]> pendingQuantityBySku();

    /** Quantity on PENDING receipts for one ledger line (to avoid over-planning). */
    @Query("""
            select coalesce(sum(ri.expectedQuantity), 0) from PurchaseReceiptItem ri, PurchaseReceipt r
            where ri.purchaseReceiptId = r.id and ri.purchaseLedgerItemId = :ledgerItemId
              and r.status = com.petitcamel.shop.purchase.domain.PurchaseEnums.ReceiptStatus.PENDING
            """)
    long pendingQuantityForLedgerItem(@Param("ledgerItemId") Long ledgerItemId);
}
