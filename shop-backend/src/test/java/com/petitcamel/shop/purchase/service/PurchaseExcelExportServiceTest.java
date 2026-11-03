package com.petitcamel.shop.purchase.service;

import com.petitcamel.shop.common.storage.UploadedFileInspector;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.EvidenceType;
import com.petitcamel.shop.purchase.domain.PurchaseEnums.KeptStatus;
import com.petitcamel.shop.purchase.domain.PurchaseLedger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseExcelExportServiceTest {

    private static final String LEDGER = "\ub9e4\uc785\uc7a5\ubd80";
    private static final String SUMMARY = "\uc6d4\ubcc4\uc694\uc57d";

    private final PurchaseExcelExportService service = new PurchaseExcelExportService(null);

    @Test
    void fillsTemplateKeepingFormulasAndComputesSummary() throws Exception {
        List<PurchaseLedger> ledgers = List.of(
                ledger(1L, "202611-001", LocalDate.of(2026, 11, 2), 180000L, 180000L, true),
                ledger(2L, "202611-002", LocalDate.of(2026, 11, 3), 50000L, 30000L, false));
        byte[] bytes = service.render(ledgers, Map.of(2L, List.of("a.png", "b.pdf")), 2026);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheet(LEDGER);
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("202611-001");
            assertThat(sheet.getRow(5).getCell(1).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(sheet.getRow(5).getCell(4).getStringCellValue()).contains("\n");
            assertThat(sheet.getRow(5).getCell(4).getCellStyle().getWrapText()).isTrue();
            Cell diff = sheet.getRow(6).getCell(10);
            assertThat(diff.getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(diff.getCellFormula()).isEqualTo("IF(F7=\"\",\"\",F7-G7)");
            assertThat(diff.getNumericCellValue()).isEqualTo(20000d);
            assertThat(sheet.getRow(5).getCell(16).getStringCellValue()).isEqualTo("\uae30\ubcf8\uc790\ub8cc \ubcf4\uad00");
            assertThat(sheet.getRow(6).getCell(16).getStringCellValue()).isEqualTo("\uc790\ub8cc \ud655\uc778 \ud544\uc694");
            assertThat(sheet.getRow(6).getCell(14).getStringCellValue()).isEqualTo("a.png\nb.pdf");
            assertThat(sheet.getTables().get(0).getArea().formatAsString()).isEqualTo("A5:Q505");

            XSSFSheet summary = wb.getSheet(SUMMARY);
            assertThat(summary.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(2026d);
            // November row (A16) purchase total
            assertThat(summary.getRow(15).getCell(1).getNumericCellValue()).isEqualTo(230000d);
            assertThat(summary.getRow(15).getCell(3).getNumericCellValue()).isEqualTo(2d);
        }
    }

    @Test
    void extendsTableFormattingAndSummaryBeyondFiveHundredRows() throws Exception {
        List<PurchaseLedger> ledgers = new ArrayList<>();
        for (int i = 1; i <= 520; i++) {
            ledgers.add(ledger((long) i, String.format("202610-%03d", i), LocalDate.of(2026, 10, 1), 1000L, 1000L, true));
        }
        byte[] bytes = service.render(ledgers, Map.of(), 2026);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheet(LEDGER);
            assertThat(sheet.getTables().get(0).getArea().formatAsString()).isEqualTo("A5:Q525");
            assertThat(sheet.getRow(524).getCell(0).getStringCellValue()).isEqualTo("202610-520");
            assertThat(sheet.getRow(524).getCell(10).getCellFormula()).isEqualTo("IF(F525=\"\",\"\",F525-G525)");
            assertThat(sheet.getSheetConditionalFormatting().getConditionalFormattingAt(0).getFormattingRanges()[0]
                    .formatAsString()).endsWith("525");
            XSSFSheet summary = wb.getSheet(SUMMARY);
            assertThat(summary.getRow(14).getCell(1).getCellFormula()).contains("$525").doesNotContain("$505");
            assertThat(summary.getRow(14).getCell(1).getNumericCellValue()).isEqualTo(520000d);
        }
    }

    @Test
    void inspectorChecksRealFileType() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(UploadedFileInspector.inspect("a.png", png, UploadedFileInspector.IMAGE_AND_PDF_TYPES, 1000)
                .contentType()).isEqualTo("image/png");
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> UploadedFileInspector.inspect("a.png", script,
                UploadedFileInspector.IMAGE_AND_PDF_TYPES, 1000)).isInstanceOf(BusinessException.class);
        byte[] pdf = "%PDF-1.7 x".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> UploadedFileInspector.inspect("a.pdf", pdf, UploadedFileInspector.IMAGE_TYPES, 1000))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> UploadedFileInspector.inspect("a.png", png, UploadedFileInspector.IMAGE_TYPES, 5))
                .isInstanceOf(BusinessException.class);
    }

    private static PurchaseLedger ledger(Long id, String no, LocalDate date, Long purchase, Long payment, boolean complete) {
        PurchaseLedger ledger = new PurchaseLedger() {
            @Override
            public Long getId() {
                return id;
            }
        };
        ledger.setManagementNo(no);
        ledger.setPurchaseDate(date);
        ledger.setSupplierName("A\uc758\ub958");
        ledger.setSupplierBusinessNo(complete ? "220-81-62517" : null);
        ledger.setItemSummary("\uc6d0\ud53c\uc2a4/\uac80/FREE 10\u00d718,000\uc6d0\n\ud2f0\uc154\uce20 1\u00d71,000\uc6d0");
        ledger.setPurchaseAmount(purchase);
        ledger.setPaymentAmount(payment);
        ledger.setPaymentDate(date);
        ledger.setRecipientName("\ud64d\uae38\ub3d9");
        ledger.setStatementKept(KeptStatus.KEPT);
        ledger.setTransferKept(KeptStatus.KEPT);
        ledger.setEvidenceType(EvidenceType.NONE);
        return ledger;
    }
}
