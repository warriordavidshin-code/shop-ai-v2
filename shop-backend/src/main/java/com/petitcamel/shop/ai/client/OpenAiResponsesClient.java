package com.petitcamel.shop.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.ai.config.AiAnalysisProperties;
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
import java.util.Base64;

/**
 * Calls {@code POST {baseUrl}/responses} with strict JSON-schema output. Images are sent as
 * {@code input_image} data URLs and PDFs as {@code input_file}, so no document conversion runs on the server.
 * Request bodies (which contain the files) and the API key are never logged.
 */
@Component
public class OpenAiResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);
    private static final int MAX_ERROR_DETAIL = 200;

    private final AiAnalysisProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiResponsesClient(AiAnalysisProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public AiStructuredResult createStructured(AiStructuredRequest request) {
        if (!properties.hasApiKey()) {
            throw new AiClientException(AiClientException.Type.NOT_CONFIGURED,
                    "OpenAI API 키(OPENAI_API_KEY)가 설정되지 않았습니다.");
        }
        String model = properties.effectiveModel();
        String body = buildBody(request, model);
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        AiClientException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.currentTimeMillis();
            try {
                AiStructuredResult result = send(body, model, attempt);
                log.info("OpenAI responses call ok schema={} model={} attempt={} inTokens={} outTokens={} ms={}",
                        request.schemaName(), result.model(), attempt, result.inputTokens(), result.outputTokens(),
                        System.currentTimeMillis() - started);
                return result;
            } catch (AiClientException ex) {
                last = ex.withAttempts(attempt);
                log.warn("OpenAI responses call failed schema={} model={} attempt={}/{} type={} ms={}",
                        request.schemaName(), model, attempt, maxAttempts, ex.getType(),
                        System.currentTimeMillis() - started);
                if (!ex.getType().isRetryable() || attempt == maxAttempts) {
                    throw last;
                }
                sleepBackoff(attempt, ex);
            }
        }
        throw last;
    }

    private AiStructuredResult send(String body, String model, int attempt) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(trimSlash(properties.getBaseUrl()) + "/responses"))
                .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            throw new AiClientException(AiClientException.Type.TIMEOUT, "AI 응답 시간이 초과되었습니다.", ex);
        } catch (IOException ex) {
            throw new AiClientException(AiClientException.Type.NETWORK, "AI 서버에 연결하지 못했습니다.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiClientException(AiClientException.Type.NETWORK, "AI 요청이 중단되었습니다.", ex);
        }
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new AiClientException(AiClientException.Type.AUTHENTICATION,
                    "OpenAI 인증에 실패했습니다. API 키와 권한을 확인해 주세요.");
        }
        if (status == 429) {
            RateLimited rateLimited = new RateLimited("AI 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
            rateLimited.retryAfterSeconds = response.headers().firstValue("retry-after")
                    .map(OpenAiResponsesClient::parseLongOrNull).orElse(null);
            throw rateLimited;
        }
        if (status >= 500) {
            throw new AiClientException(AiClientException.Type.SERVER_ERROR,
                    "AI 서버 오류가 발생했습니다. (HTTP " + status + ")");
        }
        if (status >= 400) {
            throw new AiClientException(AiClientException.Type.BAD_REQUEST,
                    "AI 요청이 거부되었습니다. (HTTP " + status + ") " + errorDetail(response.body()));
        }
        return parse(response.body(), model, attempt);
    }

    AiStructuredResult parse(String responseBody, String requestedModel, int attempt) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 응답을 해석할 수 없습니다.", ex);
        }
        String responseStatus = root.path("status").asText("");
        if ("incomplete".equals(responseStatus)) {
            String reason = root.path("incomplete_details").path("reason").asText("unknown");
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE,
                    "AI 응답이 완료되지 않았습니다. (" + reason + ")");
        }
        if ("failed".equals(responseStatus)) {
            throw new AiClientException(AiClientException.Type.SERVER_ERROR, "AI 분석이 실패했습니다.");
        }
        String text = null;
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                String type = content.path("type").asText();
                if ("refusal".equals(type)) {
                    throw new AiClientException(AiClientException.Type.REFUSED,
                            "AI가 이 파일의 분석을 거절했습니다.");
                }
                if ("output_text".equals(type)) {
                    text = content.path("text").asText(null);
                }
            }
        }
        if (text == null || text.isBlank()) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 응답에 결과가 없습니다.");
        }
        JsonNode output;
        try {
            output = objectMapper.readTree(text);
        } catch (JsonProcessingException ex) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 결과가 JSON 형식이 아닙니다.", ex);
        }
        if (!output.isObject()) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 결과가 JSON 객체가 아닙니다.");
        }
        JsonNode usage = root.path("usage");
        Integer in = usage.hasNonNull("input_tokens") ? usage.get("input_tokens").asInt() : null;
        Integer out = usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asInt() : null;
        String model = root.path("model").asText(requestedModel);
        return new AiStructuredResult(output, model, in, out, attempt);
    }

    String buildBody(AiStructuredRequest request, String model) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("instructions", request.instructions());
        body.put("store", false);
        body.put("max_output_tokens", properties.getMaxOutputTokens());
        ArrayNode input = body.putArray("input");
        ObjectNode message = input.addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        content.addObject().put("type", "input_text").put("text", request.userText());
        Base64.Encoder encoder = Base64.getEncoder();
        int index = 0;
        for (AiInputFile file : request.files()) {
            content.addObject().put("type", "input_text").put("text", "[파일 " + index + "]");
            String dataUrl = "data:" + file.contentType() + ";base64," + encoder.encodeToString(file.data());
            if (file.isPdf()) {
                content.addObject()
                        .put("type", "input_file")
                        .put("filename", "document-" + index + ".pdf")
                        .put("file_data", dataUrl);
            } else {
                content.addObject()
                        .put("type", "input_image")
                        .put("image_url", dataUrl)
                        .put("detail", "high");
            }
            index++;
        }
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", request.schemaName());
        format.put("strict", true);
        format.set("schema", request.schema());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new AiClientException(AiClientException.Type.BAD_REQUEST, "AI 요청을 만들 수 없습니다.", ex);
        }
    }

    private void sleepBackoff(int attempt, AiClientException ex) {
        long waitMs = properties.getRetryBackoffMs() * attempt;
        if (ex instanceof RateLimited rateLimited && rateLimited.retryAfterSeconds != null) {
            waitMs = Math.max(waitMs, Math.min(rateLimited.retryAfterSeconds, 30) * 1000L);
        }
        if (waitMs <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    private String errorDetail(String body) {
        try {
            String message = objectMapper.readTree(body).path("error").path("message").asText("");
            return message.length() > MAX_ERROR_DETAIL ? message.substring(0, MAX_ERROR_DETAIL) : message;
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String trimSlash(String url) {
        String value = url == null || url.isBlank() ? "https://api.openai.com/v1" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static final class RateLimited extends AiClientException {
        private Long retryAfterSeconds;

        private RateLimited(String message) {
            super(Type.RATE_LIMITED, message);
        }
    }
}
