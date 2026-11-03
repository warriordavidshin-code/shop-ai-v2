package com.petitcamel.shop.purchase.service;

import com.petitcamel.shop.purchase.domain.PurchaseEnums.AllocationMethod;
import com.petitcamel.shop.purchase.domain.PurchaseReceipt;
import com.petitcamel.shop.purchase.domain.PurchaseReceiptItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseLogicTest {

    @Test
    void allocatesExtraCostByQuantityWithExactTotal() {
        PurchaseReceipt receipt = receipt(AllocationMethod.QUANTITY, 1000);
        PurchaseReceiptItem a = line(1, 18000, 2);
        PurchaseReceiptItem b = line(2, 20000, 1);
        ReceivingService.allocate(List.of(a, b), receipt);

        assertThat(a.getAllocatedCost().add(b.getAllocatedCost())).isEqualByComparingTo("1000");
        assertThat(a.getAllocatedCost()).isEqualByComparingTo("666.67");
        assertThat(b.getAllocatedCost()).isEqualByComparingTo("333.33");
        assertThat(a.getLandedUnitCost()).isEqualByComparingTo("18333.3350");
    }

    @Test
    void allocatesExtraCostByAmount() {
        PurchaseReceipt receipt = receipt(AllocationMethod.AMOUNT, 3000);
        PurchaseReceiptItem a = line(1, 10000, 1);
        PurchaseReceiptItem b = line(2, 20000, 1);
        PurchaseReceiptItem notReceived = line(3, 50000, 0);
        ReceivingService.allocate(List.of(a, b, notReceived), receipt);

        assertThat(a.getAllocatedCost()).isEqualByComparingTo("1000");
        assertThat(b.getAllocatedCost()).isEqualByComparingTo("2000");
        assertThat(notReceived.getAllocatedCost()).isEqualByComparingTo("0");
        assertThat(b.getLandedUnitCost()).isEqualByComparingTo("22000");
    }

    @Test
    void noExtraCostKeepsUnitCost() {
        PurchaseReceipt receipt = receipt(AllocationMethod.AMOUNT, 0);
        PurchaseReceiptItem a = line(1, 18000, 10);
        ReceivingService.allocate(List.of(a), receipt);
        assertThat(a.getLandedUnitCost()).isEqualByComparingTo("18000");
    }

    @Test
    void businessNumberChecksum() {
        assertThat(AiReceiptService.validBusinessNo("220-81-62517")).isTrue();
        assertThat(AiReceiptService.validBusinessNo("220-81-62518")).isFalse();
        assertThat(AiReceiptService.validBusinessNo("12345")).isFalse();
        assertThat(AiReceiptService.validBusinessNo(null)).isFalse();
    }

    @Test
    void nameSimilarityRanksSameProductHigh() {
        assertThat(ProductNameSimilarity.score("모건 버뮤다 팬츠", "모건버뮤다")).isGreaterThan(0.6);
        assertThat(ProductNameSimilarity.score("헤더 앙고라 티셔츠", "린넨 와이드 슬랙스")).isLessThan(0.35);
        assertThat(ProductNameSimilarity.score("Basic Tee Tee", "basic tee")).isGreaterThan(0.6);
    }

    @Test
    void excelTextStartingLikeFormulaIsEscaped() {
        assertThat(PurchaseExcelExportService.safeText("=HYPERLINK(\"x\")")).startsWith("'=");
        assertThat(PurchaseExcelExportService.safeText("123-45-67890")).isEqualTo("123-45-67890");
    }

    private static PurchaseReceipt receipt(AllocationMethod method, long freight) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setAllocationMethod(method);
        receipt.setFreightCost(freight);
        receipt.setIncidentalCost(0L);
        receipt.setPackagingCost(0L);
        receipt.setOtherCost(0L);
        return receipt;
    }

    private static PurchaseReceiptItem line(long id, long unitCost, int qty) {
        PurchaseReceiptItem item = new PurchaseReceiptItem();
        item.setUnitCost(unitCost);
        item.setExpectedQuantity(qty);
        item.setReceivedQuantity(qty);
        return item;
    }
}
