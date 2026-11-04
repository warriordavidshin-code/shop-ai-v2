package com.petitcamel.shop.purchase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.ai.client.LocalOcrClient.OcrLine;
import com.petitcamel.shop.ai.client.LocalOcrClient.OcrResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptTextParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReceiptTextParser parser = new ReceiptTextParser(objectMapper, 0.75, LocalDate.of(2026, 11, 4));

    /** Real PaddleOCR output of fixtures/receipt-sample.png (two OCR misreads: colour text and quantity "5"). */
    @Test
    void parsesRealPaddleOcrOutputAndFlagsMisreadsForReview() throws Exception {
        ReceiptTextParser.Parsed parsed = parser.parse(List.of(fixture("/fixtures/receipt-sample.ocr.json")));
        JsonNode out = parsed.output();

        assertThat(out.path("documents").get(0).path("document_type").asText()).isEqualTo("STATEMENT");
        assertThat(out.path("supplier_business_no").asText()).isEqualTo("124-81-00998");
        assertThat(out.path("supplier_name").asText()).isEqualTo("테스트상사");
        assertThat(out.path("purchase_date").asText()).isEqualTo("2026-11-03");
        assertThat(out.path("purchase_amount").asLong()).isEqualTo(250000);

        JsonNode items = out.path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("product_name").asText()).startsWith("니트");
        assertThat(items.get(0).path("size").asText()).isEqualTo("FREE");
        assertThat(items.get(0).path("quantity").asInt()).isEqualTo(10);
        assertThat(items.get(0).path("unit_price").asLong()).isEqualTo(15000);
        assertThat(items.get(0).path("amount").asLong()).isEqualTo(150000);
        assertThat(items.get(1).path("product_name").asText()).startsWith("슬랙스");
        assertThat(items.get(1).path("quantity").asInt()).isEqualTo(5);
        assertThat(items.get(1).path("unit_price").asLong()).isEqualTo(20000);

        assertThat(parsed.uncertain()).isTrue();
        assertThat(parsed.reasons()).anyMatch(r -> r.contains("수량×단가"));
        assertThat(out.path("warnings").toString()).contains("로컬 OCR");
    }

    @Test
    void cleanStatementIsCertain() {
        List<OcrLine> lines = new ArrayList<>();
        String[] rows = {"거래명세서", "등록번호 124-81-00998", "상호 맑은상사", "거래일자 2026-11-02",
                "블라우스 화이트 FREE 4 12,000 48,000", "스커트 블랙 M 2 21,000 42,000", "합계금액 90,000"};
        for (int i = 0; i < rows.length; i++) {
            lines.add(new OcrLine(0, rows[i], 0.99, 40, 40 + i * 50, 900, 75 + i * 50));
        }
        ReceiptTextParser.Parsed parsed = parser.parse(List.of(new OcrResult("paddleocr", 1, lines)));
        assertThat(parsed.reasons()).isEmpty();
        assertThat(parsed.uncertain()).isFalse();
        JsonNode items = parsed.output().path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("color").asText()).isEqualTo("화이트");
        assertThat(items.get(1).path("quantity").asInt()).isEqualTo(2);
        assertThat(parsed.output().path("purchase_amount").asLong()).isEqualTo(90000);
    }

    @Test
    void missingTotalAndLowConfidenceAreUncertain() {
        List<OcrLine> lines = List.of(new OcrLine(0, "영수증", 0.4, 0, 0, 100, 20),
                new OcrLine(0, "니트 1 10,000 10,000", 0.5, 0, 30, 100, 50));
        ReceiptTextParser.Parsed parsed = parser.parse(List.of(new OcrResult("paddleocr", 1, lines)));
        assertThat(parsed.uncertain()).isTrue();
        assertThat(parsed.reasons()).anyMatch(r -> r.contains("신뢰도"));
    }

    private OcrResult fixture(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            JsonNode root = objectMapper.readTree(in);
            List<OcrLine> lines = new ArrayList<>();
            for (JsonNode line : root.path("lines")) {
                JsonNode box = line.path("box");
                double left = Double.MAX_VALUE;
                double top = Double.MAX_VALUE;
                double right = 0;
                double bottom = 0;
                for (JsonNode p : box) {
                    left = Math.min(left, p.get(0).asDouble());
                    top = Math.min(top, p.get(1).asDouble());
                    right = Math.max(right, p.get(0).asDouble());
                    bottom = Math.max(bottom, p.get(1).asDouble());
                }
                lines.add(new OcrLine(line.path("page").asInt(), line.path("text").asText(),
                        line.path("confidence").asDouble(), left, top, right, bottom));
            }
            return new OcrResult(root.path("engine").asText(), root.path("pages").asInt(), lines);
        }
    }
}
