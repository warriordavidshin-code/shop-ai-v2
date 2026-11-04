package com.petitcamel.shop.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.ai.config.AiCostProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to the on-server PaddleOCR sidecar ({@code ocr-service}). No external network, no API key, no cost.
 * File bytes are sent as the raw request body and are never logged.
 */
@Component
public class LocalOcrClient {

    private static final Logger log = LoggerFactory.getLogger(LocalOcrClient.class);

    public record OcrLine(int page, String text, double confidence, double left, double top, double right,
                          double bottom) {
        public double centerY() {
            return (top + bottom) / 2;
        }

        public double height() {
            return Math.max(1, bottom - top);
        }
    }

    public record OcrResult(String engine, int pages, List<OcrLine> lines) {
        public double averageConfidence() {
            return lines.stream().mapToDouble(OcrLine::confidence).average().orElse(0);
        }
    }

    private final AiCostProperties.Ocr properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LocalOcrClient(AiCostProperties costProperties, ObjectMapper objectMapper) {
        this.properties = costProperties.ocr();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.connectTimeoutSeconds())))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public OcrResult recognize(AiInputFile file) {
        long started = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(properties.url()) + "/ocr"))
                .timeout(Duration.ofSeconds(Math.max(5, properties.timeoutSeconds())))
                .header("Content-Type", file.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(file.data()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            throw new AiClientException(AiClientException.Type.TIMEOUT, "로컬 OCR 응답 시간이 초과되었습니다.", ex);
        } catch (IOException ex) {
            throw new AiClientException(AiClientException.Type.NETWORK, "로컬 OCR 서버에 연결하지 못했습니다.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiClientException(AiClientException.Type.NETWORK, "로컬 OCR 요청이 중단되었습니다.", ex);
        }
        int status = response.statusCode();
        if (status == 413 || status == 415) {
            throw new AiClientException(AiClientException.Type.BAD_REQUEST,
                    "로컬 OCR이 처리할 수 없는 파일입니다. (HTTP " + status + ")");
        }
        if (status >= 400) {
            throw new AiClientException(AiClientException.Type.SERVER_ERROR,
                    "로컬 OCR 처리에 실패했습니다. (HTTP " + status + ")");
        }
        OcrResult result = parse(response.body());
        log.info("Local OCR ok pages={} lines={} ms={}", result.pages(), result.lines().size(),
                System.currentTimeMillis() - started);
        return result;
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(properties.url()) + "/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    OcrResult parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "로컬 OCR 응답을 해석할 수 없습니다.", ex);
        }
        List<OcrLine> lines = new ArrayList<>();
        for (JsonNode line : root.path("lines")) {
            String text = line.path("text").asText("").trim();
            if (text.isEmpty()) {
                continue;
            }
            double left = Double.MAX_VALUE;
            double top = Double.MAX_VALUE;
            double right = 0;
            double bottom = 0;
            for (JsonNode point : line.path("box")) {
                double x = point.path(0).asDouble();
                double y = point.path(1).asDouble();
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
            if (left == Double.MAX_VALUE) {
                left = 0;
                top = lines.size() * 20.0;
                right = 0;
                bottom = top + 10;
            }
            lines.add(new OcrLine(line.path("page").asInt(0), text, line.path("confidence").asDouble(0),
                    left, top, right, bottom));
        }
        return new OcrResult(root.path("engine").asText("paddleocr"), root.path("pages").asInt(1), lines);
    }

    private static String trimSlash(String url) {
        String value = url == null || url.isBlank() ? "http://localhost:8866" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
