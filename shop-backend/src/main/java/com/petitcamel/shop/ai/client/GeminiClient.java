package com.petitcamel.shop.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petitcamel.shop.ai.config.AiCostProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Calls Gemini {@code POST {baseUrl}/models/{model}:generateContent} with JSON output. The structured-output
 * schema is sent as {@code responseJsonSchema}; if the API rejects the schema, the call is retried once with the
 * schema embedded in the instructions (the server validates the result either way). HTTP 429 means the free
 * quota is used up and is reported as {@link AiClientException.Type#RATE_LIMITED} without retrying, so the router
 * can move on. Request bodies and the API key are never logged.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final int MAX_ERROR_DETAIL = 200;

    private final AiCostProperties.Gemini properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiClient(AiCostProperties costProperties, ObjectMapper objectMapper) {
        this.properties = costProperties.gemini();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.connectTimeoutSeconds())))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String model() {
        return properties.model();
    }

    public AiStructuredResult createStructured(AiStructuredRequest request) {
        if (!properties.hasApiKey()) {
            throw new AiClientException(AiClientException.Type.NOT_CONFIGURED,
                    "Gemini API 키(GEMINI_API_KEY)가 설정되지 않았습니다.");
        }
        String model = properties.model();
        boolean nativeSchema = true;
        int maxAttempts = Math.max(1, properties.maxRetries() + 1);
        AiClientException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.currentTimeMillis();
            try {
                AiStructuredResult result = send(buildBody(request, nativeSchema), model, attempt);
                log.info("Gemini call ok schema={} model={} attempt={} inTokens={} outTokens={} ms={}",
                        request.schemaName(), result.model(), attempt, result.inputTokens(), result.outputTokens(),
                        System.currentTimeMillis() - started);
                return result;
            } catch (SchemaRejected ex) {
                last = ex.withAttempts(attempt);
                log.warn("Gemini rejected responseJsonSchema schema={}; retrying with schema in prompt",
                        request.schemaName());
                if (!nativeSchema) {
                    throw last;
                }
                nativeSchema = false;
                maxAttempts = Math.max(maxAttempts, attempt + 1);
            } catch (AiClientException ex) {
                last = ex.withAttempts(attempt);
                log.warn("Gemini call failed schema={} model={} attempt={}/{} type={} ms={}",
                        request.schemaName(), model, attempt, maxAttempts, ex.getType(),
                        System.currentTimeMillis() - started);
                if (ex.getType() == AiClientException.Type.RATE_LIMITED
                        || !ex.getType().isRetryable() || attempt == maxAttempts) {
                    throw last;
                }
                sleepBackoff(attempt, ex);
            }
        }
        throw last;
    }

    private AiStructuredResult send(String body, String model, int attempt) {
        String url = trimSlash(properties.baseUrl()) + "/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, properties.timeoutSeconds())))
                .header("x-goog-api-key", properties.apiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            throw new AiClientException(AiClientException.Type.TIMEOUT, "Gemini 응답 시간이 초과되었습니다.", ex);
        } catch (IOException ex) {
            throw new AiClientException(AiClientException.Type.NETWORK, "Gemini 서버에 연결하지 못했습니다.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiClientException(AiClientException.Type.NETWORK, "Gemini 요청이 중단되었습니다.", ex);
        }
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new AiClientException(AiClientException.Type.AUTHENTICATION,
                    "Gemini 인증에 실패했습니다. API 키와 권한을 확인해 주세요.");
        }
        if (status == 429) {
            throw new AiClientException(AiClientException.Type.RATE_LIMITED,
                    "Gemini 무료 사용 한도를 초과했습니다.");
        }
        if (status >= 500) {
            throw new AiClientException(AiClientException.Type.SERVER_ERROR,
                    "Gemini 서버 오류가 발생했습니다. (HTTP " + status + ")");
        }
        if (status >= 400) {
            String detail = errorDetail(response.body());
            if (status == 400 && detail.toLowerCase().contains("schema")) {
                throw new SchemaRejected(detail);
            }
            throw new AiClientException(AiClientException.Type.BAD_REQUEST,
                    "Gemini 요청이 거부되었습니다. (HTTP " + status + ") " + detail);
        }
        return parse(response.body(), model, attempt);
    }

    AiStructuredResult parse(String responseBody, String requestedModel, int attempt) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "Gemini 응답을 해석할 수 없습니다.", ex);
        }
        if (root.path("promptFeedback").hasNonNull("blockReason")) {
            throw new AiClientException(AiClientException.Type.REFUSED, "Gemini가 이 파일의 분석을 거절했습니다.");
        }
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        if ("SAFETY".equals(finish) || "PROHIBITED_CONTENT".equals(finish) || "RECITATION".equals(finish)) {
            throw new AiClientException(AiClientException.Type.REFUSED, "Gemini가 이 파일의 분석을 거절했습니다.");
        }
        if ("MAX_TOKENS".equals(finish)) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE,
                    "Gemini 응답이 완료되지 않았습니다. (max_tokens)");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            if (part.hasNonNull("text")) {
                text.append(part.get("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "Gemini 응답에 결과가 없습니다.");
        }
        JsonNode output;
        try {
            output = objectMapper.readTree(stripFence(text.toString()));
        } catch (JsonProcessingException ex) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE,
                    "Gemini 결과가 JSON 형식이 아닙니다.", ex);
        }
        if (output == null || !output.isObject()) {
            throw new AiClientException(AiClientException.Type.INVALID_RESPONSE,
                    "Gemini 결과가 JSON 객체가 아닙니다.");
        }
        JsonNode usage = root.path("usageMetadata");
        Integer in = usage.hasNonNull("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null;
        Integer out = null;
        if (usage.hasNonNull("candidatesTokenCount") || usage.hasNonNull("thoughtsTokenCount")) {
            out = usage.path("candidatesTokenCount").asInt(0) + usage.path("thoughtsTokenCount").asInt(0);
        }
        String model = root.path("modelVersion").asText(requestedModel);
        return new AiStructuredResult(output, model, in, out, attempt);
    }

    String buildBody(AiStructuredRequest request, boolean nativeSchema) {
        ObjectNode body = objectMapper.createObjectNode();
        String instructions = request.instructions();
        if (!nativeSchema) {
            instructions = instructions + "\n\n반드시 다음 JSON 스키마를 정확히 따르는 JSON 객체 하나만 출력하라. "
                    + "모든 키를 포함하고, 값이 없으면 null을 사용하라.\n" + request.schema().toString();
        }
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", instructions);
        ArrayNode contents = body.putArray("contents");
        ObjectNode message = contents.addObject();
        message.put("role", "user");
        ArrayNode parts = message.putArray("parts");
        parts.addObject().put("text", request.userText());
        Base64.Encoder encoder = Base64.getEncoder();
        int index = 0;
        for (AiInputFile file : request.files()) {
            parts.addObject().put("text", "[파일 " + index + "]");
            ObjectNode inline = parts.addObject().putObject("inline_data");
            inline.put("mime_type", file.contentType());
            inline.put("data", encoder.encodeToString(file.data()));
            index++;
        }
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.put("temperature", 0.2);
        config.put("maxOutputTokens", properties.maxOutputTokens());
        if (nativeSchema) {
            config.set("responseJsonSchema", request.schema());
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new AiClientException(AiClientException.Type.BAD_REQUEST, "Gemini 요청을 만들 수 없습니다.", ex);
        }
    }

    private static String stripFence(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return value.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return value;
    }

    private void sleepBackoff(int attempt, AiClientException ex) {
        long waitMs = properties.retryBackoffMs() * attempt;
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

    private static String trimSlash(String url) {
        String value = url == null || url.isBlank() ? "https://generativelanguage.googleapis.com/v1beta" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static final class SchemaRejected extends AiClientException {
        private SchemaRejected(String detail) {
            super(Type.BAD_REQUEST, "Gemini가 응답 스키마를 거부했습니다. " + detail);
        }
    }
}
