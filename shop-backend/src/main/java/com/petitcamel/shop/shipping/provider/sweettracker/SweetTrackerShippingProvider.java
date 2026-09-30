package com.petitcamel.shop.shipping.provider.sweettracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petitcamel.shop.shipping.config.ShippingProperties;
import com.petitcamel.shop.shipping.domain.ShipmentStatus;
import com.petitcamel.shop.shipping.provider.ShippingProvider;
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
 * 스마트택배(SweetTracker) tracking API: {@code GET /api/v1/trackingInfo?t_key&t_code&t_invoice}.
 * The API key travels as a query parameter, so request URLs and raw client errors are never logged.
 */
@Component
public class SweetTrackerShippingProvider implements ShippingProvider {

    public static final String NAME = "SWEETTRACKER";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_STRING = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShippingProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SweetTrackerShippingProvider(ShippingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTracking().getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getTracking().getReadTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isConfigured() {
        return hasText(properties.getApiKey()) && hasText(properties.getSweettracker().getBaseUrl());
    }

    @Override
    public TrackingResponse tracking(String companyCode, String trackingNumber) {
        if (!isConfigured()) {
            throw new ShippingProviderException("스마트택배 API 키가 설정되지 않았습니다.");
        }
        String body;
        try {
            body = restClient.get()
                    .uri(properties.getSweettracker().getBaseUrl()
                                    + "/api/v1/trackingInfo?t_key={key}&t_code={code}&t_invoice={invoice}",
                            properties.getApiKey(), companyCode, trackingNumber)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new ShippingProviderException("스마트택배 응답 오류 HTTP " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new ShippingProviderException("스마트택배 연결 실패: "
                    + ShippingLogMasker.scrub(ex.getClass().getSimpleName(), properties.getApiKey()));
        }
        return parse(body);
    }

    TrackingResponse parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body == null ? "" : body);
        } catch (Exception ex) {
            throw new ShippingProviderException("스마트택배 응답을 해석할 수 없습니다.");
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new ShippingProviderException("스마트택배 응답이 비어 있습니다.");
        }

        if (root.has("status") && !root.path("status").asBoolean(true)) {
            String msg = root.path("msg").asText("배송 정보를 찾을 수 없습니다.");
            if (looksLikeAuthError(msg, root.path("code").asText(""))) {
                throw new ShippingProviderException("스마트택배 인증 실패(API 키 확인 필요)");
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
                    truncate(detail.path("where").asText("").trim(), 100),
                    truncate(kind.isEmpty() ? "배송 정보 갱신" : kind, 300),
                    status));
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
