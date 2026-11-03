package com.petitcamel.shop.purchase.domain;

import com.petitcamel.shop.common.domain.BaseEntity;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Line item of a statement / receipt, optionally matched to an internal product SKU. */
@Entity
@Table(name = "purchase_ledger_item")
public class PurchaseLedgerItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_ledger_item_id")
    private Long id;

    @Column(name = "purchase_ledger_id", nullable = false)
    private Long purchaseLedgerId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "option_name", length = 200)
    private String optionName;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "size", length = 30)
    private String size;

    @Column(name = "supplier_product_code", length = 100)
    private String supplierProductCode;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price")
    private Long unitPrice;

    @Column(name = "supply_amount")
    private Long supplyAmount;

    @Column(name = "tax_amount")
    private Long taxAmount;

    @Column(name = "total_amount")
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private MatchStatus matchStatus = MatchStatus.UNMATCHED;

    @Column(name = "matched_product_id")
    private Long matchedProductId;

    @Column(name = "matched_sku_id")
    private Long matchedSkuId;

    @Column(name = "match_score", precision = 5, scale = 4)
    private BigDecimal matchScore;

    @Column(name = "match_note", length = 500)
    private String matchNote;

    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity = 0;

    /** Unit cost for receiving: unit price, else total / quantity. */
    public Long effectiveUnitCost() {
        if (unitPrice != null) {
            return unitPrice;
        }
        if (totalAmount != null && quantity != null && quantity > 0) {
            return Math.round((double) totalAmount / quantity);
        }
        return null;
    }

    public int remainingQuantity() {
        int ordered = quantity == null ? 0 : quantity;
        return Math.max(0, ordered - (receivedQuantity == null ? 0 : receivedQuantity));
    }

    public Long getId() {
        return id;
    }

    public Long getPurchaseLedgerId() {
        return purchaseLedgerId;
    }

    public void setPurchaseLedgerId(Long purchaseLedgerId) {
        this.purchaseLedgerId = purchaseLedgerId;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getOptionName() {
        return optionName;
    }

    public void setOptionName(String optionName) {
        this.optionName = optionName;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getSupplierProductCode() {
        return supplierProductCode;
    }

    public void setSupplierProductCode(String supplierProductCode) {
        this.supplierProductCode = supplierProductCode;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Long unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Long getSupplyAmount() {
        return supplyAmount;
    }

    public void setSupplyAmount(Long supplyAmount) {
        this.supplyAmount = supplyAmount;
    }

    public Long getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(Long taxAmount) {
        this.taxAmount = taxAmount;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(MatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    public Long getMatchedProductId() {
        return matchedProductId;
    }

    public void setMatchedProductId(Long matchedProductId) {
        this.matchedProductId = matchedProductId;
    }

    public Long getMatchedSkuId() {
        return matchedSkuId;
    }

    public void setMatchedSkuId(Long matchedSkuId) {
        this.matchedSkuId = matchedSkuId;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(BigDecimal matchScore) {
        this.matchScore = matchScore;
    }

    public String getMatchNote() {
        return matchNote;
    }

    public void setMatchNote(String matchNote) {
        this.matchNote = matchNote;
    }

    public Integer getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(Integer receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }
}
