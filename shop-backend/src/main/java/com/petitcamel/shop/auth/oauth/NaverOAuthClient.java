package com.petitcamel.shop.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.domain.Gender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class NaverOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(NaverOAuthClient.class);
    private static final String AUTHORIZE_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_URL = "https://openapi.naver.com/v1/nid/me";

    private final OAuthProperties properties;
    private final RestClient restClient;

    public NaverOAuthClient(OAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    public boolean isAvailable() {
        return properties.getNaver().isConfigured();
    }

    public String buildAuthorizeUrl(String state) {
        ensureConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", properties.getNaver().getClientId())
                .queryParam("redirect_uri", properties.callbackUrl("naver"))
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    public SocialProfile exchange(String code, String state) {
        ensureConfigured();
        String accessToken = requestAccessToken(code, state);
        return fetchProfile(accessToken);
    }

    private String requestAccessToken(String code, String state) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.getNaver().getClientId());
        form.add("client_secret", properties.getNaver().getClientSecret());
        form.add("code", code);
        form.add("state", state == null ? "" : state);

        JsonNode tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("Naver token exchange failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "네이버 로그인에 실패했습니다.");
        }
        if (tokenResponse == null || !tokenResponse.hasNonNull("access_token")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "네이버 로그인에 실패했습니다.");
        }
        return tokenResponse.get("access_token").asText();
    }

    private SocialProfile fetchProfile(String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(USER_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("Naver user profile fetch failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "네이버 사용자 정보를 가져오지 못했습니다.");
        }
        if (body == null || !"00".equals(textOrNull(body, "resultcode"))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "네이버 사용자 정보를 가져오지 못했습니다.");
        }
        JsonNode response = body.path("response");
        String providerUserId = textOrNull(response, "id");
        if (providerUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "네이버 사용자 정보를 가져오지 못했습니다.");
        }

        String email = textOrNull(response, "email");
        String name = firstNonBlank(
                textOrNull(response, "name"),
                textOrNull(response, "nickname"),
                "네이버회원");
        String image = textOrNull(response, "profile_image");
        LocalDate birthDate = parseNaverBirth(
                textOrNull(response, "birthyear"),
                textOrNull(response, "birthday"));
        Gender gender = mapNaverGender(textOrNull(response, "gender"));

        return new SocialProfile(AuthProvider.NAVER, providerUserId, email, name, image, birthDate, gender);
    }

    private void ensureConfigured() {
        if (!isAvailable()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "네이버 로그인이 설정되지 않았습니다.");
        }
    }

    private static LocalDate parseNaverBirth(String birthyear, String birthday) {
        if (birthyear == null || birthday == null) {
            return null;
        }
        String normalized = birthday.contains("-") ? birthday : null;
        if (normalized == null && birthday.length() == 5 && birthday.charAt(2) == '-') {
            normalized = birthday;
        }
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(birthyear + "-" + normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Gender mapNaverGender(String raw) {
        if (raw == null) {
            return Gender.PREFER_NOT_TO_SAY;
        }
        return switch (raw.toUpperCase()) {
            case "F" -> Gender.FEMALE;
            case "M" -> Gender.MALE;
            default -> Gender.PREFER_NOT_TO_SAY;
        };
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
