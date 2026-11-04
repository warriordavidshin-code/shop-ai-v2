package com.petitcamel.shop.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.ai.config.AiCostProperties;
import com.petitcamel.shop.ai.support.StrictSchema;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientTest {

    private HttpServer server;
    private final List<String> bodies = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final List<int[]> plan = new ArrayList<>();
    private final List<String> responses = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta", ex -> {
            int i = calls.getAndIncrement();
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(ex.getRequestHeaders().getFirst("x-goog-api-key") + " " + ex.getRequestURI().getPath());
            int status = plan.get(Math.min(i, plan.size() - 1))[0];
            byte[] bytes = responses.get(Math.min(i, responses.size() - 1)).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiClient client(int maxRetries) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
        AiCostProperties props = new AiCostProperties("FREE_FIRST", false, false, 20, BigDecimal.ONE, true, false,
                new AiCostProperties.Gemini("test-key", true, "gemini-2.5-flash", base, true,
                        new BigDecimal("0.30"), new BigDecimal("2.50"), 5, 10, maxRetries, 1, 1000),
                new AiCostProperties.Ocr("http://127.0.0.1:9", 1, 5, 0.75));
        return new GeminiClient(props, new ObjectMapper());
    }

    private void respond(int status, String body) {
        plan.add(new int[]{status});
        responses.add(body);
    }

    private static AiStructuredRequest request() {
        return new AiStructuredRequest("t", StrictSchema.object().prop("name", StrictSchema.string("n")).build(),
                "규칙", "분석하라", List.of(new AiInputFile("a.png", "image/png", new byte[]{1, 2, 3})));
    }

    @Test
    void sendsKeyInHeaderAndParsesJsonAndUsage() {
        respond(200, """
                {"candidates":[{"content":{"parts":[{"text":"생각중","thought":true},
                  {"text":"```json\\n{\\"name\\":\\"셔츠\\"}\\n```"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":100,"candidatesTokenCount":20,"thoughtsTokenCount":5},
                 "modelVersion":"gemini-2.5-flash"}
                """);
        AiStructuredResult result = client(0).createStructured(request());
        assertThat(result.output().path("name").asText()).isEqualTo("셔츠");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(25);
        assertThat(keys.get(0)).isEqualTo("test-key /v1beta/models/gemini-2.5-flash:generateContent");
        assertThat(bodies.get(0)).doesNotContain("test-key").contains("inline_data").contains("responseJsonSchema");
    }

    @Test
    void rateLimitIsNotRetried() {
        respond(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");
        assertThatThrownBy(() -> client(2).createStructured(request()))
                .isInstanceOfSatisfying(AiClientException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(AiClientException.Type.RATE_LIMITED));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void schemaRejectionFallsBackToPromptSchema() {
        respond(400, "{\"error\":{\"code\":400,\"message\":\"Invalid JSON payload: responseJsonSchema not supported\"}}");
        respond(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"name\\\":\\\"니트\\\"}\"}]},"
                + "\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":2}}");
        AiStructuredResult result = client(0).createStructured(request());
        assertThat(result.output().path("name").asText()).isEqualTo("니트");
        assertThat(calls.get()).isEqualTo(2);
        assertThat(bodies.get(1)).doesNotContain("responseJsonSchema");
    }

    @Test
    void blockedAndAuthErrorsAreClassified() {
        respond(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertThatThrownBy(() -> client(0).createStructured(request()))
                .isInstanceOfSatisfying(AiClientException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(AiClientException.Type.REFUSED));
        plan.clear();
        responses.clear();
        respond(403, "{\"error\":{\"code\":403}}");
        assertThatThrownBy(() -> client(0).createStructured(request()))
                .isInstanceOfSatisfying(AiClientException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(AiClientException.Type.AUTHENTICATION));
    }
}
