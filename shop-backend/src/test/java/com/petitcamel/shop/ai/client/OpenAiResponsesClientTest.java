package com.petitcamel.shop.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.ai.config.AiAnalysisProperties;
import com.petitcamel.shop.ai.support.JsonSchemaValidator;
import com.petitcamel.shop.ai.support.StrictSchema;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real HTTP client against a local fake of the OpenAI Responses endpoint. */
class OpenAiResponsesClientTest {

    private static final String OK_BODY = """
            {"id":"resp_1","status":"completed","model":"gpt-4.1-mini-2025",
             "output":[{"type":"message","content":[{"type":"output_text","text":"{\\"supplier_name\\":\\"A의류\\"}"}]}],
             "usage":{"input_tokens":120,"output_tokens":30}}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private final ConcurrentLinkedQueue<Object[]> responses = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> requestBodies = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> authHeaders = new ConcurrentLinkedQueue<>();
    private final AtomicInteger calls = new AtomicInteger();
    private AiAnalysisProperties properties;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            Object[] next = responses.poll();
            int status = next == null ? 500 : (int) next[0];
            byte[] body = (next == null ? "{}" : (String) next[1]).getBytes(StandardCharsets.UTF_8);
            if (status == 429) {
                exchange.getResponseHeaders().add("retry-after", "0");
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        properties = new AiAnalysisProperties();
        properties.setApiKey("test-key-not-real");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.setMaxRetries(2);
        properties.setRetryBackoffMs(1);
        properties.setTimeoutSeconds(10);
        properties.setConnectTimeoutSeconds(5);
        properties.setMaxOutputTokens(500);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AiStructuredRequest request(List<AiInputFile> files) {
        return new AiStructuredRequest("receipt", StrictSchema.object()
                .prop("supplier_name", StrictSchema.nullableString("상호")).build(),
                "instructions", "analyze", files);
    }

    @Test
    void sendsStrictSchemaRequestAndParsesOutput() throws Exception {
        responses.add(new Object[]{200, OK_BODY});
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] pdf = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);
        AiStructuredResult result = new OpenAiResponsesClient(properties, objectMapper).createStructured(request(
                List.of(new AiInputFile("a.png", "image/png", png), new AiInputFile("b.pdf", "application/pdf", pdf))));

        assertThat(result.output().path("supplier_name").asText()).isEqualTo("A의류");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(30);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(authHeaders.peek()).isEqualTo("Bearer test-key-not-real");

        JsonNode sent = objectMapper.readTree(requestBodies.peek());
        assertThat(sent.path("store").asBoolean(true)).isFalse();
        assertThat(sent.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(sent.path("text").path("format").path("strict").asBoolean()).isTrue();
        String content = sent.toString();
        assertThat(content).contains("input_image").contains("data:image/png;base64,").contains("input_file");
    }

    @Test
    void retriesRateLimitThenSucceeds() {
        responses.add(new Object[]{429, "{\"error\":{\"message\":\"slow down\"}}"});
        responses.add(new Object[]{200, OK_BODY});
        AiStructuredResult result = new OpenAiResponsesClient(properties, objectMapper).createStructured(request(List.of()));
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void authenticationFailureIsNotRetried() {
        responses.add(new Object[]{401, "{\"error\":{\"message\":\"bad key\"}}"});
        assertThatThrownBy(() -> new OpenAiResponsesClient(properties, objectMapper).createStructured(request(List.of())))
                .isInstanceOf(AiClientException.class)
                .satisfies(ex -> assertThat(((AiClientException) ex).getType())
                        .isEqualTo(AiClientException.Type.AUTHENTICATION));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void refusalIsReportedAsError() {
        responses.add(new Object[]{200, """
                {"status":"completed","model":"m","output":[{"type":"message","content":[{"type":"refusal","refusal":"no"}]}]}
                """});
        assertThatThrownBy(() -> new OpenAiResponsesClient(properties, objectMapper).createStructured(request(List.of())))
                .isInstanceOf(AiClientException.class)
                .satisfies(ex -> assertThat(((AiClientException) ex).getType())
                        .isEqualTo(AiClientException.Type.REFUSED));
    }

    @Test
    void serverErrorsExhaustRetries() {
        responses.add(new Object[]{500, "{}"});
        responses.add(new Object[]{502, "{}"});
        responses.add(new Object[]{503, "{}"});
        assertThatThrownBy(() -> new OpenAiResponsesClient(properties, objectMapper).createStructured(request(List.of())))
                .isInstanceOf(AiClientException.class)
                .satisfies(ex -> assertThat(((AiClientException) ex).getAttempts()).isEqualTo(3));
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void missingKeyFailsWithoutCallingServer() {
        properties.setApiKey("");
        assertThatThrownBy(() -> new OpenAiResponsesClient(properties, objectMapper).createStructured(request(List.of())))
                .isInstanceOf(AiClientException.class)
                .satisfies(ex -> assertThat(((AiClientException) ex).getType())
                        .isEqualTo(AiClientException.Type.NOT_CONFIGURED));
        assertThat(calls.get()).isZero();
    }

    @Test
    void schemaValidatorRejectsMissingAndExtraFields() throws Exception {
        var schema = StrictSchema.object()
                .prop("name", StrictSchema.string("n"))
                .prop("qty", StrictSchema.nullableInteger("q"))
                .prop("kind", StrictSchema.enumOf("k", "A", "B"))
                .build();
        assertThat(JsonSchemaValidator.validate(schema, objectMapper.readTree(
                "{\"name\":\"x\",\"qty\":null,\"kind\":\"A\"}"))).isEmpty();
        assertThat(JsonSchemaValidator.validate(schema, objectMapper.readTree(
                "{\"name\":\"x\",\"kind\":\"C\",\"extra\":1}"))).hasSizeGreaterThanOrEqualTo(3);
        assertThat(JsonSchemaValidator.validate(schema, objectMapper.readTree(
                "{\"name\":1,\"qty\":1.5,\"kind\":\"B\"}"))).hasSize(2);
    }
}
