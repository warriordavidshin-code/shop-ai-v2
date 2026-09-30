package com.petitcamel.shop.shipping.provider.sweettracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.ProviderCapability;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.provider.ShippingProviderClient;
import com.petitcamel.shop.shipping.provider.ShippingProviderException;
import com.petitcamel.shop.shipping.provider.TrackingResponse;
import com.petitcamel.shop.shipping.provider.TrackingStatusMapper;
import com.petitcamel.shop.shipping.support.ShippingLogMasker;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 스마트택배(SweetTracker) API: tracking ({@code GET /api/v1/trackingInfo}) and the courier list used as a
 * connection test ({@code GET /api/v1/companylist}). The API key travels as a query parameter, so request URLs and
 * raw client errors are never logged.
 */
@Component
public class SweetTrackerShippingClient implements ShippingProviderClient {

    public static final String CODE = "SWEETTRACKER";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_STRING = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShippingProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SweetTrackerShippingClient(ShippingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTracking().getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getTracking().getReadTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return hasText(properties.getApiKey()) && hasText(properties.getSweettracker().getBaseUrl());
    }

    @Override
    public boolean supports(ProviderCapability capability) {
        return capability == ProviderCapability.TRACKING;
    }

    @Override
    public TrackingResponse tracking(String externalCompanyCode, String trackingNumber) {
        requireConfigured();
        String body = get("/api/v1/trackingInfo?t_key={key}&t_code={code}&t_invoice={invoice}",
                properties.getApiKey(), externalCompanyCode, trackingNumber);
        return parse(body);
    }

    @Override
    public String testConnection() {
        requireConfigured();
        JsonNode root = readJson(get("/api/v1/companylist?t_key={key}", properties.getApiKey()));
        if (root.has("status") && !root.path("status").asBoolean(true)) {
            String msg = root.path("msg").asText("");
            if (looksLikeAuthError(msg, root.path("code").asText(""))) {
                throw new ShippingProviderException("스마트택배 인증 실패(API 키 확인 필요)", null, "AUTH", false);
            }
            throw new ShippingProviderException("스마트택배 응답 오류: " + truncate(msg, 100), null, "REJECTED", false);
        }
        int companies = root.path("Company").size();
        return "스마트택배 연결 성공 (택배사 " + companies + "곳 조회)";
    }

    TrackingResponse parse(String body) {
        JsonNode root = readJson(body);
        if (root.has("status") && !root.path("status").asBoolean(true)) {
            String msg = root.path("msg").asText("배송 정보를 찾을 수 없습니다.");
            if (looksLikeAuthError(msg, root.path("code").asText(""))) {
                throw new ShippingProviderException("스마트택배 인증 실패(API 키 확인 필요)", null, "AUTH", false);
            }
            return TrackingResponse.notFound(msg);
        }

        List<TrackingResponse.Event> events = new ArrayList<>();
        for (JsonNode detail : root.path("trackingDetails")) {
            Instant time = parseTime(detail);
            if (time == null) {
                continue;
            }
            String kind = detail.path("kind").asText("").trim();
            ShipmentStatus status = TrackingStatusMapper.fromSweetTrackerLevel(
                    detail.hasNonNull("level") ? detail.path("level").asInt() : null);
            if (status == null) {
                status = TrackingStatusMapper.fromText(kind);
            }
            events.add(new TrackingResponse.Event(
                    time,
                    null,
                    kind.isEmpty() ? null : truncate(kind, 100),
                    status,
                    truncate(detail.path("where").asText("").trim(), 100),
                    truncate(kind.isEmpty() ? "배송 정보 갱신" : kind, 300)));
        }
        events.sort(Comparator.comparing(TrackingResponse.Event::time));

        ShipmentStatus status = root.path("complete").asBoolean(false)
                ? ShipmentStatus.DELIVERED
                : TrackingStatusMapper.fromSweetTrackerLevel(root.hasNonNull("level") ? root.path("level").asInt() : null);
        if (status == null && !events.isEmpty()) {
            status = events.get(events.size() - 1).status();
        }
        return new TrackingResponse(true, status, List.copyOf(events), null);
    }

    private String get(String pathAndQuery, Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(properties.getSweettracker().getBaseUrl() + pathAndQuery, uriVariables)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            throw new ShippingProviderException("스마트택배 응답 오류 HTTP " + status, status, "HTTP_" + status, false);
        } catch (RestClientException ex) {
            throw new ShippingProviderException("스마트택배 연결 실패: "
                    + ShippingLogMasker.scrub(ex.getClass().getSimpleName(), properties.getApiKey()),
                    null, "CONNECTION", false);
        }
    }

    private JsonNode readJson(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body == null ? "" : body);
        } catch (Exception ex) {
            throw new ShippingProviderException("스마트택배 응답을 해석할 수 없습니다.", null, "PARSE", false);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new ShippingProviderException("스마트택배 응답이 비어 있습니다.", null, "EMPTY", false);
        }
        return root;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ShippingProviderException("스마트택배 API 키가 설정되지 않았습니다.", null, "NOT_CONFIGURED", false);
        }
    }

    private static boolean looksLikeAuthError(String msg, String code) {
        String lower = msg.toLowerCase();
        return lower.contains("key") || msg.contains("인증") || "101".equals(code);
    }

    private static Instant parseTime(JsonNode detail) {
        if (detail.hasNonNull("time") && detail.path("time").canConvertToLong() && detail.path("time").asLong() > 0) {
            return Instant.ofEpochMilli(detail.path("time").asLong());
        }
        String text = detail.path("timeString").asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), TIME_STRING).atZone(SEOUL).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
