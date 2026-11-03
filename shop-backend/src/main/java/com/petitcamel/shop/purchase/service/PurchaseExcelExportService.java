package com.petitcamel.shop.purchase.service;

import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.purchase.domain.PurchaseLedger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 매입장부 Excel 내보내기. Fills the bundled company template (same 17 columns, styles, table, conditional
 * formatting, validations and 월별요약 formulas). When there are more than the template's 500 data rows, the
 * table range, formatting, validations and summary formulas are extended to the real last row.
 */
@Service
public class PurchaseExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseExcelExportService.class);
    static final String TEMPLATE = "templates/purchase-ledger-template.xlsx";
    static final int FIRST_DATA_ROW = 5; // zero-based row index of Excel row 6
    static final int TEMPLATE_LAST_ROW = 504; // zero-based row index of Excel row 505
    public static final int MAX_EXPORT_ROWS = 20_000;
    private static final String LEDGER_SHEET = "\ub9e4\uc785\uc7a5\ubd80";
    private static final String SUMMARY_SHEET = "\uc6d4\ubcc4\uc694\uc57d";

    public record Export(byte[] content, String fileName, int rowCount) {
    }

    private final PurchaseLedgerService ledgerService;

    public PurchaseExcelExportService(PurchaseLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public Export export(PurchaseLedgerService.LedgerFilter filter, String fileName, Integer summaryYear) {
        List<PurchaseLedger> ledgers = ledgerService.search(filter, MAX_EXPORT_ROWS + 1);
        if (ledgers.size() > MAX_EXPORT_ROWS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "한 번에 내보낼 수 있는 행은 " + MAX_EXPORT_ROWS + "건입니다. 기간을 나눠서 내보내 주세요.");
        }
        Map<Long, List<String>> evidenceNames = ledgerService.evidenceNamesByLedger(
                ledgers.stream().map(PurchaseLedger::getId).toList());
        int year = summaryYear != null ? summaryYear : ledgers.stream().map(PurchaseLedger::getPurchaseDate)
                .max(LocalDate::compareTo).map(LocalDate::getYear)
                .orElse(LocalDate.now(ZoneId.of("Asia/Seoul")).getYear());
        try {
            byte[] content = render(ledgers, evidenceNames, year);
            return new Export(content, fileName, ledgers.size());
        } catch (IOException ex) {
            log.error("Purchase ledger export failed", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "엑셀 파일을 만들지 못했습니다.");
        }
    }

    byte[] render(List<PurchaseLedger> ledgers, Map<Long, List<String>> evidenceNames, int year) throws IOException {
        try (InputStream in = new ClassPathResource(TEMPLATE).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            XSSFSheet sheet = workbook.getSheet(LEDGER_SHEET);
            XSSFSheet summary = workbook.getSheet(SUMMARY_SHEET);
            if (sheet == null || summary == null) {
                throw new IOException("Template sheets missing");
            }
            int lastRow = Math.max(TEMPLATE_LAST_ROW, FIRST_DATA_ROW + ledgers.size() - 1);
            if (lastRow > TEMPLATE_LAST_ROW) {
                extend(workbook, sheet, summary, lastRow);
            }

            Row styleRow = sheet.getRow(FIRST_DATA_ROW + 1);
            CellStyle wrapText = workbook.createCellStyle();
            wrapText.cloneStyleFrom(styleRow.getCell(4).getCellStyle());
            wrapText.setWrapText(true);
            CellStyle wrapMemo = workbook.createCellStyle();
            wrapMemo.cloneStyleFrom(styleRow.getCell(15).getCellStyle());
            wrapMemo.setWrapText(true);
            CellStyle wrapFiles = workbook.createCellStyle();
            wrapFiles.cloneStyleFrom(styleRow.getCell(14).getCellStyle());
            wrapFiles.setWrapText(true);

            for (int i = 0; i < ledgers.size(); i++) {
                PurchaseLedger ledger = ledgers.get(i);
                Row row = sheet.getRow(FIRST_DATA_ROW + i);
                setText(row, 0, ledger.getManagementNo());
                setDate(row, 1, ledger.getPurchaseDate());
                setText(row, 2, ledger.getSupplierName());
                setText(row, 3, ledger.getSupplierBusinessNo());
                setText(row, 4, ledger.getItemSummary()).setCellStyle(wrapText);
                setNumber(row, 5, ledger.getPurchaseAmount());
                setNumber(row, 6, ledger.getPaymentAmount());
                setDate(row, 7, ledger.getPaymentDate());
                setText(row, 8, ledger.getRecipientName());
                setText(row, 9, ledger.getTransferReference());
                setText(row, 11, ledger.getStatementKept() == null ? null : ledger.getStatementKept().label());
                setText(row, 12, ledger.getTransferKept() == null ? null : ledger.getTransferKept().label());
                setText(row, 13, ledger.getEvidenceType() == null ? null : ledger.getEvidenceType().label());
                String files = ledger.getEvidenceLocation();
                List<String> names = evidenceNames.getOrDefault(ledger.getId(), List.of());
                if ((files == null || files.isBlank()) && !names.isEmpty()) {
                    files = String.join("\n", names);
                }
                setText(row, 14, files).setCellStyle(wrapFiles);
                setText(row, 15, ledger.getRemarks()).setCellStyle(wrapMemo);
                int lines = Math.max(lineCount(ledger.getItemSummary(), 30),
                        Math.max(lineCount(files, 24), lineCount(ledger.getRemarks(), 30)));
                row.setHeightInPoints(Math.max(24f, Math.min(lines * 15f + 4f, 409f)));
            }

            Row yearRow = summary.getRow(2);
            yearRow.getCell(1).setCellValue(year);

            workbook.setForceFormulaRecalculation(true);
            try {
                XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
            } catch (RuntimeException ex) {
                // Excel recalculates on open (fullCalcOnLoad); cached values are a convenience for viewers.
                log.warn("Formula pre-evaluation skipped: {}", ex.getMessage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void extend(XSSFWorkbook workbook, XSSFSheet sheet, XSSFSheet summary, int lastRow) {
        Row template = sheet.getRow(TEMPLATE_LAST_ROW);
        for (int r = TEMPLATE_LAST_ROW + 1; r <= lastRow; r++) {
            Row row = sheet.createRow(r);
            row.setHeight(template.getHeight());
            int excelRow = r + 1;
            for (int c = 0; c < 17; c++) {
                Cell source = template.getCell(c);
                Cell cell = row.createCell(c);
                if (source != null) {
                    cell.setCellStyle(source.getCellStyle());
                }
            }
            row.getCell(10).setCellFormula("IF(F" + excelRow + "=\"\",\"\",F" + excelRow + "-G" + excelRow + ")");
            row.getCell(16).setCellFormula(qFormula(excelRow));
        }
        String last = String.valueOf(lastRow + 1);

        for (XSSFTable table : sheet.getTables()) {
            if ("PurchaseLedger".equals(table.getName())) {
                table.setArea(new AreaReference(new CellReference(4, 0), new CellReference(lastRow, 16),
                        SpreadsheetVersion.EXCEL2007));
            }
        }

        XSSFSheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        for (int i = 0; i < formatting.getNumConditionalFormattings(); i++) {
            XSSFConditionalFormatting cf = formatting.getConditionalFormattingAt(i);
            CellRangeAddress[] ranges = cf.getFormattingRanges();
            List<CellRangeAddress> updated = new ArrayList<>();
            for (CellRangeAddress range : ranges) {
                if (range.getLastRow() == TEMPLATE_LAST_ROW) {
                    updated.add(new CellRangeAddress(range.getFirstRow(), lastRow, range.getFirstColumn(),
                            range.getLastColumn()));
                } else {
                    updated.add(range);
                }
            }
            cf.setFormattingRanges(updated.toArray(new CellRangeAddress[0]));
        }

        DataValidationHelper helper = sheet.getDataValidationHelper();
        addList(sheet, helper, new String[]{"\ubcf4\uad00", "\ubbf8\ubcf4\uad00", "\ud574\ub2f9\uc5c6\uc74c"},
                new CellRangeAddressList(TEMPLATE_LAST_ROW + 1, lastRow, 11, 12));
        addList(sheet, helper, new String[]{"\uc5c6\uc74c", "\uc138\uae08\uacc4\uc0b0\uc11c",
                        "\ud604\uae08\uc601\uc218\uc99d(\uc9c0\ucd9c\uc99d\ube59)", "\uce74\ub4dc\uc804\ud45c", "\uacc4\uc0b0\uc11c"},
                new CellRangeAddressList(TEMPLATE_LAST_ROW + 1, lastRow, 13, 13));

        for (Row row : summary) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.FORMULA) {
                    String formula = cell.getCellFormula();
                    if (formula.contains("$505")) {
                        cell.setCellFormula(formula.replace("$505", "$" + last));
                    }
                }
            }
        }
        workbook.setForceFormulaRecalculation(true);
    }

    private static void addList(Sheet sheet, DataValidationHelper helper, String[] values, CellRangeAddressList range) {
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    static String qFormula(int r) {
        return "IF(A" + r + "=\"\",\"\",IF(OR(B" + r + "=\"\",C" + r + "=\"\",D" + r + "=\"\",F" + r + "=\"\",G" + r
                + "=\"\",H" + r + "=\"\",I" + r + "=\"\",L" + r + "<>\"\ubcf4\uad00\",M" + r
                + "<>\"\ubcf4\uad00\"),\"\uc790\ub8cc \ud655\uc778 \ud544\uc694\",\"\uae30\ubcf8\uc790\ub8cc \ubcf4\uad00\"))";
    }

    private static Cell setText(Row row, int column, String value) {
        Cell cell = cell(row, column);
        if (value == null || value.isBlank()) {
            cell.setBlank();
        } else {
            cell.setCellValue(safeText(value));
        }
        return cell;
    }

    /** Cell text that starts like a formula is prefixed so spreadsheet apps never evaluate imported text. */
    static String safeText(String value) {
        String text = value.length() > 32_000 ? value.substring(0, 32_000) : value;
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + text;
        }
        return text;
    }

    private static void setNumber(Row row, int column, Long value) {
        Cell cell = cell(row, column);
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value.doubleValue());
        }
    }

    private static void setDate(Row row, int column, LocalDate value) {
        Cell cell = cell(row, column);
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value);
        }
    }

    private static Cell cell(Row row, int column) {
        Cell cell = row.getCell(column);
        return cell == null ? row.createCell(column) : cell;
    }

    private static int lineCount(String text, int charsPerLine) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int lines = 0;
        for (String line : text.split("\\r?\\n")) {
            lines += Math.max(1, (int) Math.ceil(line.length() / (double) charsPerLine));
        }
        return lines;
    }
}
