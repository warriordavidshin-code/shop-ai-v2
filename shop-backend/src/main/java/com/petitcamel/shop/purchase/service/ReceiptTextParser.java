package com.petitcamel.shop.purchase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.ai.client.LocalOcrClient.OcrLine;
import com.petitcamel.shop.ai.client.LocalOcrClient.OcrResult;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java rules that turn PaddleOCR text lines into the receipt draft JSON (same shape as the AI receipt schema):
 * supplier, business number (checksum), date, items (name / quantity / unit price / amount), supply, tax, total
 * and payment. The result says whether it is {@link Parsed#uncertain() uncertain}; only then is an AI model
 * consulted. Values that cannot be read stay null — nothing is guessed.
 */
public class ReceiptTextParser {

    private static final Pattern BUSINESS_NO = Pattern.compile("(?<!\\d)(\\d{3})\\s*[-.\\s]\\s*(\\d{2})\\s*[-.\\s]\\s*(\\d{5})(?!\\d)|(?<!\\d)(\\d{10})(?!\\d)");
    private static final Pattern DATE_FULL = Pattern.compile("(20\\d{2})\\s*[-./년]\\s*(\\d{1,2})\\s*[-./월]\\s*(\\d{1,2})");
    private static final Pattern DATE_SHORT = Pattern.compile("(?<!\\d)(\\d{2})[-./](\\d{2})[-./](\\d{2})(?!\\d)");
    private static final Pattern MONEY = Pattern.compile("(?<![\\d.])(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\s*원)?(?![\\d.])");
    private static final Pattern SUPPLIER_LABEL = Pattern.compile(
            "(상\\s*호\\s*명?|가\\s*맹\\s*점\\s*명?|판\\s*매\\s*자|업\\s*체\\s*명|법\\s*인\\s*명)\\s*[:：)]?\\s*(.+)");
    private static final Pattern RECIPIENT_LABEL = Pattern.compile("(받는\\s*분|수\\s*취\\s*인|예\\s*금\\s*주)\\s*[:：]?\\s*(.+)");
    private static final Pattern TOTAL_LABEL = Pattern.compile("(합\\s*계|총\\s*액|총\\s*금\\s*액|결\\s*제\\s*금\\s*액|받\\s*을\\s*금\\s*액|청\\s*구\\s*금\\s*액|판\\s*매\\s*총\\s*액|TOTAL)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPLY_LABEL = Pattern.compile("공\\s*급\\s*가\\s*액?|과\\s*세\\s*물\\s*품\\s*가\\s*액");
    private static final Pattern TAX_LABEL = Pattern.compile("세\\s*액|부\\s*가\\s*세|VAT", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSFER_AMOUNT_LABEL = Pattern.compile("이\\s*체\\s*금\\s*액|송\\s*금\\s*액|출\\s*금\\s*액|보\\s*낸\\s*금\\s*액|이\\s*체\\s*액");
    private static final Pattern DATE_LABEL = Pattern.compile("거래\\s*일|작성\\s*일|일\\s*자|날\\s*짜|승인\\s*일|발행\\s*일|거래\\s*일시|이체\\s*일");
    private static final Pattern HEADER_WORD = Pattern.compile("품\\s*명|품\\s*목|상\\s*품\\s*명|수\\s*량|단\\s*가|금\\s*액|규\\s*격");
    private static final Pattern STOP_ROW = Pattern.compile("합\\s*계|소\\s*계|총\\s*액|공\\s*급\\s*가|세\\s*액|부\\s*가\\s*세|결\\s*제|받\\s*을|카\\s*드|승\\s*인|과\\s*세|면\\s*세|현\\s*금|거스름|잔\\s*액|이\\s*하\\s*여\\s*백");
    private static final Set<String> COLORS = Set.of("블랙", "화이트", "아이보리", "베이지", "네이비", "그레이", "차콜",
            "핑크", "레드", "와인", "블루", "스카이", "소라", "민트", "그린", "카키", "옐로우", "브라운", "카멜", "퍼플",
            "라벤더", "오렌지", "크림", "멜란지", "연청", "중청", "진청", "흑청", "검정", "흰색");
    private static final Pattern SIZE = Pattern.compile("^(FREE|F|XS|S|M|L|XL|XXL|2XL|3XL|44|55|66|77|88|\\d{2,3})$",
            Pattern.CASE_INSENSITIVE);

    public record Parsed(ObjectNode output, boolean uncertain, List<String> reasons, double ocrConfidence,
                         String ocrText) {
    }

    record Row(int page, String text, double confidence, double top) {
    }

    private record DocInfo(String type, double typeConfidence, List<Row> rows) {
    }

    private final ObjectMapper objectMapper;
    private final double minConfidence;
    private final LocalDate today;

    public ReceiptTextParser(ObjectMapper objectMapper, double minConfidence, LocalDate today) {
        this.objectMapper = objectMapper;
        this.minConfidence = minConfidence;
        this.today = today;
    }

    public Parsed parse(List<OcrResult> files) {
        List<DocInfo> docs = new ArrayList<>();
        StringBuilder ocrText = new StringBuilder();
        double confidenceSum = 0;
        int lineCount = 0;
        for (int i = 0; i < files.size(); i++) {
            OcrResult file = files.get(i);
            List<Row> rows = rows(file.lines());
            docs.add(new DocInfo(documentType(rows), rows.isEmpty() ? 0 : 0.8, rows));
            ocrText.append("[파일 ").append(i).append("]\n");
            rows.forEach(r -> ocrText.append(r.text()).append('\n'));
            for (OcrLine line : file.lines()) {
                confidenceSum += line.confidence();
                lineCount++;
            }
        }
        double ocrConfidence = lineCount == 0 ? 0 : confidenceSum / lineCount;
        List<String> reasons = new ArrayList<>();

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode documents = root.putArray("documents");
        for (int i = 0; i < docs.size(); i++) {
            DocInfo doc = docs.get(i);
            documents.addObject().put("file_index", i).put("document_type", doc.type())
                    .put("confidence", "UNKNOWN".equals(doc.type()) ? 0.3 : round(doc.typeConfidence()));
        }

        String supplier = null;
        boolean supplierLabelled = false;
        String businessNo = null;
        LocalDate purchaseDate = null;
        Long supply = null;
        Long tax = null;
        Long total = null;
        Long payment = null;
        LocalDate paymentDate = null;
        String recipient = null;
        String paymentMethod = null;
        String evidenceType = null;
        ArrayNode items = root.putArray("items");
        boolean itemsConsistent = true;

        for (DocInfo doc : docs) {
            List<Row> rows = doc.rows();
            if ("TRANSFER".equals(doc.type())) {
                Long amount = labelledAmount(rows, TRANSFER_AMOUNT_LABEL);
                if (amount == null) {
                    amount = labelledAmount(rows, Pattern.compile("금\\s*액"));
                }
                if (payment == null) {
                    payment = amount;
                }
                if (paymentDate == null) {
                    paymentDate = date(rows);
                }
                if (recipient == null) {
                    recipient = labelledText(rows, RECIPIENT_LABEL);
                }
                paymentMethod = "TRANSFER";
                continue;
            }
            if (supplier == null) {
                String labelled = labelledText(rows, SUPPLIER_LABEL);
                if (labelled != null) {
                    supplier = labelled;
                    supplierLabelled = true;
                } else if (!"STATEMENT".equals(doc.type())) {
                    supplier = firstNameLikeRow(rows);
                }
            }
            if (businessNo == null) {
                businessNo = businessNo(rows);
            }
            if (purchaseDate == null) {
                purchaseDate = date(rows);
            }
            if (supply == null) {
                supply = labelledAmount(rows, SUPPLY_LABEL);
            }
            if (tax == null) {
                tax = labelledAmount(rows, TAX_LABEL);
            }
            if (total == null) {
                total = totalAmount(rows);
            }
            itemsConsistent &= items(rows, items);
            switch (doc.type()) {
                case "CARD_RECEIPT" -> {
                    paymentMethod = "CARD";
                    evidenceType = "CARD_SLIP";
                    if (payment == null) {
                        payment = total;
                    }
                }
                case "CASH_RECEIPT" -> {
                    paymentMethod = paymentMethod == null ? "CASH" : paymentMethod;
                    evidenceType = "CASH_RECEIPT";
                    if (payment == null) {
                        payment = total;
                    }
                }
                case "TAX_INVOICE" -> evidenceType = "TAX_INVOICE";
                default -> {
                }
            }
        }
        if (total == null && supply != null && tax != null) {
            total = supply + tax;
            reasons.add("합계 금액을 직접 읽지 못해 공급가액+세액으로 계산했습니다.");
        }

        root.put("supplier_name", supplier);
        root.put("supplier_business_no", businessNo);
        root.put("purchase_date", purchaseDate == null ? null : purchaseDate.toString());
        putLong(root, "supply_amount", supply);
        putLong(root, "tax_amount", tax);
        putLong(root, "purchase_amount", total);
        putLong(root, "payment_amount", payment);
        root.put("payment_date", paymentDate == null ? null : paymentDate.toString());
        root.put("payment_method", paymentMethod);
        root.put("recipient_name", recipient);
        root.putNull("transfer_reference");
        root.put("evidence_type", evidenceType);

        long itemSum = 0;
        boolean itemSumComplete = true;
        for (var item : items) {
            if (item.path("amount").isNumber()) {
                itemSum += item.path("amount").asLong();
            } else {
                itemSumComplete = false;
            }
        }
        if (ocrConfidence < minConfidence) {
            reasons.add("OCR 인식 신뢰도가 낮습니다(" + round(ocrConfidence) + ").");
        }
        if (supplier == null) {
            reasons.add("거래처 상호를 찾지 못했습니다.");
        }
        if (purchaseDate == null) {
            reasons.add("거래일을 찾지 못했습니다.");
        }
        if (total == null) {
            reasons.add("합계 금액을 찾지 못했습니다.");
        }
        boolean onlyTransfers = docs.stream().allMatch(d -> "TRANSFER".equals(d.type()));
        if (items.isEmpty() && !onlyTransfers) {
            reasons.add("품목을 분리하지 못했습니다.");
        }
        if (!itemsConsistent) {
            reasons.add("일부 품목의 수량×단가와 금액이 맞지 않습니다.");
        }
        if (total != null && !items.isEmpty() && itemSumComplete && itemSum != total
                && !(supply != null && itemSum == supply)) {
            reasons.add("품목 합계(" + itemSum + ")가 합계 금액(" + total + ")과 다릅니다.");
        }
        if (docs.stream().anyMatch(d -> "UNKNOWN".equals(d.type()))) {
            reasons.add("문서 종류를 판별하지 못한 파일이 있습니다.");
        }

        double base = Math.min(1, ocrConfidence);
        ObjectNode fc = root.putObject("field_confidence");
        fc.put("supplier_name", round(supplier == null ? 0 : base * (supplierLabelled ? 0.9 : 0.6)));
        fc.put("purchase_date", round(purchaseDate == null ? 0 : base * 0.9));
        fc.put("purchase_amount", round(total == null ? 0 : base * 0.9));
        fc.put("payment_amount", round(payment == null ? 0 : base * 0.85));
        fc.put("items", round(items.isEmpty() ? 0 : base * (itemsConsistent ? 0.85 : 0.5)));
        ArrayNode review = root.putArray("review_reasons");
        reasons.forEach(r -> review.addObject().put("field", "localOcr").put("reason", r));
        root.putArray("warnings").add("로컬 OCR(PaddleOCR)과 규칙으로 읽은 결과입니다. 저장 전에 원본과 대조해 주세요.");
        return new Parsed(root, !reasons.isEmpty(), List.copyOf(reasons), ocrConfidence, ocrText.toString());
    }

    /** Groups OCR boxes into visual rows (same baseline) and orders each row left to right. */
    static List<Row> rows(List<OcrLine> lines) {
        List<OcrLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparingInt(OcrLine::page).thenComparingDouble(OcrLine::centerY));
        List<Row> rows = new ArrayList<>();
        List<OcrLine> current = new ArrayList<>();
        double currentCenter = 0;
        double currentHeight = 0;
        int currentPage = -1;
        for (OcrLine line : sorted) {
            boolean sameRow = !current.isEmpty() && line.page() == currentPage
                    && Math.abs(line.centerY() - currentCenter) <= Math.max(4, 0.5 * Math.min(currentHeight, line.height()));
            if (!sameRow && !current.isEmpty()) {
                rows.add(toRow(current));
                current = new ArrayList<>();
            }
            if (current.isEmpty()) {
                currentPage = line.page();
                currentCenter = line.centerY();
                currentHeight = line.height();
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            rows.add(toRow(current));
        }
        return rows;
    }

    private static Row toRow(List<OcrLine> lines) {
        lines.sort(Comparator.comparingDouble(OcrLine::left));
        StringBuilder text = new StringBuilder();
        double confidence = 0;
        double top = Double.MAX_VALUE;
        for (OcrLine line : lines) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(line.text().trim());
            confidence += line.confidence();
            top = Math.min(top, line.top());
        }
        return new Row(lines.get(0).page(), text.toString(), confidence / lines.size(), top);
    }

    static String documentType(List<Row> rows) {
        String all = String.join(" ", rows.stream().map(Row::text).toList()).replace(" ", "");
        if (all.contains("세금계산서")) {
            return "TAX_INVOICE";
        }
        if (all.contains("거래명세")) {
            return "STATEMENT";
        }
        if (all.contains("현금영수증") || all.contains("현금(소득공제)") || all.contains("현금(지출증빙)")) {
            return "CASH_RECEIPT";
        }
        if (all.contains("카드") && (all.contains("승인") || all.contains("매출전표") || all.contains("카드번호"))) {
            return "CARD_RECEIPT";
        }
        if ((all.contains("이체") || all.contains("송금") || all.contains("출금"))
                && (all.contains("받는분") || all.contains("수취인") || all.contains("예금주") || all.contains("입금계좌"))) {
            return "TRANSFER";
        }
        if (all.contains("영수증")) {
            return "OTHER";
        }
        return "UNKNOWN";
    }

    static String businessNo(List<Row> rows) {
        for (Row row : rows) {
            Matcher m = BUSINESS_NO.matcher(row.text());
            while (m.find()) {
                String digits = m.group(4) != null ? m.group(4) : m.group(1) + m.group(2) + m.group(3);
                if (AiReceiptService.validBusinessNo(digits)) {
                    return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
                }
            }
        }
        return null;
    }

    LocalDate date(List<Row> rows) {
        LocalDate unlabelled = null;
        for (Row row : rows) {
            LocalDate found = findDate(row.text());
            if (found == null) {
                continue;
            }
            if (DATE_LABEL.matcher(row.text()).find()) {
                return found;
            }
            if (unlabelled == null) {
                unlabelled = found;
            }
        }
        return unlabelled;
    }

    private LocalDate findDate(String text) {
        Matcher full = DATE_FULL.matcher(text);
        while (full.find()) {
            LocalDate date = safeDate(Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2)),
                    Integer.parseInt(full.group(3)));
            if (date != null) {
                return date;
            }
        }
        Matcher shortDate = DATE_SHORT.matcher(text);
        while (shortDate.find()) {
            LocalDate date = safeDate(2000 + Integer.parseInt(shortDate.group(1)), Integer.parseInt(shortDate.group(2)),
                    Integer.parseInt(shortDate.group(3)));
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private LocalDate safeDate(int year, int month, int day) {
        try {
            LocalDate date = LocalDate.of(year, month, day);
            if (date.isAfter(today.plusDays(1)) || date.isBefore(today.minusYears(5))) {
                return null;
            }
            return date;
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private static String labelledText(List<Row> rows, Pattern label) {
        for (Row row : rows) {
            Matcher m = label.matcher(row.text());
            if (m.find()) {
                String value = cleanName(m.group(2));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String cleanName(String raw) {
        String value = raw;
        for (String stop : List.of("성명", "성 명", "대표", "사업자", "등록번호", "주소", "전화", "TEL", "Tel", "(인)")) {
            int idx = value.indexOf(stop);
            if (idx > 0) {
                value = value.substring(0, idx);
            }
        }
        value = value.replaceAll("^[\\s:：)]+|[\\s:：(]+$", "").trim();
        if (value.length() < 2 || value.chars().filter(Character::isDigit).count() > value.length() / 2) {
            return null;
        }
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private static String firstNameLikeRow(List<Row> rows) {
        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            String text = rows.get(i).text().trim();
            String compact = text.replace(" ", "");
            if (compact.contains("영수증") || compact.contains("명세") || compact.contains("매출전표")) {
                continue;
            }
            String name = cleanName(text);
            if (name != null && name.length() <= 40) {
                return name;
            }
        }
        return null;
    }

    private static Long labelledAmount(List<Row> rows, Pattern label) {
        for (int i = 0; i < rows.size(); i++) {
            Matcher m = label.matcher(rows.get(i).text());
            if (!m.find()) {
                continue;
            }
            Long amount = lastMoney(rows.get(i).text().substring(m.end()));
            if (amount == null && i + 1 < rows.size()) {
                amount = lastMoney(rows.get(i + 1).text());
            }
            if (amount != null) {
                return amount;
            }
        }
        return null;
    }

    private static Long totalAmount(List<Row> rows) {
        Long best = null;
        for (int i = 0; i < rows.size(); i++) {
            Matcher m = TOTAL_LABEL.matcher(rows.get(i).text());
            if (!m.find()) {
                continue;
            }
            Long amount = lastMoney(rows.get(i).text().substring(m.end()));
            if (amount == null && i + 1 < rows.size()) {
                amount = lastMoney(rows.get(i + 1).text());
            }
            if (amount != null && (best == null || amount > best)) {
                best = amount;
            }
        }
        return best;
    }

    private static Long lastMoney(String text) {
        List<Long> values = money(text);
        for (int i = values.size() - 1; i >= 0; i--) {
            if (values.get(i) >= 100) {
                return values.get(i);
            }
        }
        return null;
    }

    static List<Long> money(String text) {
        List<Long> values = new ArrayList<>();
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            String digits = m.group(1).replace(",", "");
            if (digits.length() > 12) {
                continue;
            }
            values.add(Long.parseLong(digits));
        }
        return values;
    }

    /** Reads item rows; returns false when a row's quantity × unit price does not match its amount. */
    private boolean items(List<Row> rows, ArrayNode out) {
        int start = -1;
        for (int i = 0; i < rows.size(); i++) {
            Matcher m = HEADER_WORD.matcher(rows.get(i).text());
            int hits = 0;
            while (m.find()) {
                hits++;
            }
            if (hits >= 2) {
                start = i + 1;
                break;
            }
        }
        boolean consistent = true;
        int from = Math.max(0, start);
        for (int i = from; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (STOP_ROW.matcher(row.text()).find()) {
                if (start >= 0 || !out.isEmpty()) {
                    break;
                }
                continue;
            }
            ObjectNode item = item(row);
            if (item == null) {
                continue;
            }
            if (!item.path("_consistent").asBoolean()) {
                consistent = false;
            }
            item.remove("_consistent");
            out.add(item);
            if (out.size() >= 100) {
                break;
            }
        }
        return consistent;
    }

    private ObjectNode item(Row row) {
        String text = row.text().trim();
        Matcher first = MONEY.matcher(text);
        if (!first.find()) {
            return null;
        }
        String namePart = text.substring(0, first.start()).trim();
        if (BUSINESS_NO.matcher(text).find() || findDate(text) != null) {
            return null;
        }
        List<Long> numbers = money(text.substring(first.start()));
        if (namePart.length() < 2 || numbers.size() < 2 || numbers.get(numbers.size() - 1) < 100) {
            return null;
        }
        Long qty = null;
        Long unit = null;
        Long amount = numbers.get(numbers.size() - 1);
        boolean consistent = false;
        for (int a = 0; a < numbers.size() - 1 && !consistent; a++) {
            for (int b = 0; b < numbers.size() - 1; b++) {
                if (a == b) {
                    continue;
                }
                long q = numbers.get(a);
                long u = numbers.get(b);
                if (q > 0 && q <= 10_000 && u >= 10 && q * u == amount) {
                    qty = q;
                    unit = u;
                    consistent = true;
                    break;
                }
            }
        }
        if (!consistent) {
            long candidate = numbers.get(0);
            if (numbers.size() >= 3 && candidate > 0 && candidate <= 10_000) {
                qty = candidate;
                unit = numbers.get(numbers.size() - 2);
            } else if (numbers.size() == 2 && candidate >= 100 && amount % candidate == 0
                    && amount / candidate <= 10_000) {
                // quantity unreadable: derived from amount ÷ unit price, still flagged for review
                unit = candidate;
                qty = amount / candidate;
            } else if (candidate > 0 && candidate <= 1000) {
                qty = candidate;
            }
        }
        String[] tokens = namePart.split("[\\s/,()\\-]+");
        String color = null;
        String size = null;
        List<String> nameTokens = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (color == null && COLORS.contains(token)) {
                color = token;
            } else if (size == null && SIZE.matcher(token).matches() && !nameTokens.isEmpty()) {
                size = token.toUpperCase(Locale.ROOT);
            } else {
                nameTokens.add(token);
            }
        }
        String name = String.join(" ", nameTokens).trim();
        if (name.isEmpty()) {
            return null;
        }
        ObjectNode item = objectMapper.createObjectNode();
        item.put("product_name", name.length() > 200 ? name.substring(0, 200) : name);
        item.put("color", color);
        item.put("size", size);
        item.putNull("supplier_product_code");
        putLong(item, "quantity", qty);
        putLong(item, "unit_price", unit);
        putLong(item, "amount", amount);
        item.put("confidence", round(row.confidence() * (consistent ? 0.95 : 0.6)));
        item.put("_consistent", consistent);
        if (qty != null) {
            item.put("quantity", qty.intValue());
        }
        return item;
    }

    private static void putLong(ObjectNode node, String key, Long value) {
        if (value == null) {
            node.putNull(key);
        } else {
            node.put(key, value);
        }
    }

    private static double round(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 100) / 100.0;
    }
}
