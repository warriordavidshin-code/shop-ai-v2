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
public class KakaoOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoOAuthClient.class);
    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

    private final OAuthProperties properties;
    private final RestClient restClient;

    public KakaoOAuthClient(OAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    public boolean isAvailable() {
        return properties.getKakao().isConfigured();
    }

    public String buildAuthorizeUrl(String state) {
        ensureConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", properties.getKakao().getClientId())
                .queryParam("redirect_uri", properties.callbackUrl("kakao"))
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .queryParam("scope", "profile_nickname,profile_image,account_email")
                .build(true)
                .toUriString();
    }

    public SocialProfile exchange(String code) {
        ensureConfigured();
        String accessToken = requestAccessToken(code);
        return fetchProfile(accessToken);
    }

    private String requestAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.getKakao().getClientId());
        form.add("client_secret", properties.getKakao().getClientSecret());
        form.add("redirect_uri", properties.callbackUrl("kakao"));
        form.add("code", code);

        JsonNode tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("Kakao token exchange failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "카카오 로그인에 실패했습니다.");
        }
        if (tokenResponse == null || !tokenResponse.hasNonNull("access_token")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "카카오 로그인에 실패했습니다.");
        }
        return tokenResponse.get("access_token").asText();
    }

    private SocialProfile fetchProfile(String accessToken) {
        JsonNode user;
        try {
            user = restClient.get()
                    .uri(USER_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("Kakao user profile fetch failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "카카오 사용자 정보를 가져오지 못했습니다.");
        }
        if (user == null || !user.hasNonNull("id")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "카카오 사용자 정보를 가져오지 못했습니다.");
        }

        String providerUserId = user.get("id").asText();
        JsonNode account = user.path("kakao_account");
        JsonNode profile = account.path("profile");

        String email = textOrNull(account, "email");
        String name = firstNonBlank(
                textOrNull(profile, "nickname"),
                textOrNull(account, "name"),
                "카카오회원");
        String image = firstNonBlank(
                textOrNull(profile, "profile_image_url"),
                textOrNull(profile, "thumbnail_image_url"));
        LocalDate birthDate = parseKakaoBirth(account);
        Gender gender = mapKakaoGender(textOrNull(account, "gender"));

        return new SocialProfile(AuthProvider.KAKAO, providerUserId, email, name, image, birthDate, gender);
    }

    private void ensureConfigured() {
        if (!isAvailable()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "카카오 로그인이 설정되지 않았습니다.");
        }
    }

    private static LocalDate parseKakaoBirth(JsonNode account) {
        String birthyear = textOrNull(account, "birthyear");
        String birthday = textOrNull(account, "birthday");
        if (birthyear == null || birthday == null || birthday.length() != 4) {
            return null;
        }
        try {
            return LocalDate.parse(birthyear + birthday, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Gender mapKakaoGender(String raw) {
        if (raw == null) {
            return Gender.PREFER_NOT_TO_SAY;
        }
        return switch (raw.toLowerCase()) {
            case "female" -> Gender.FEMALE;
            case "male" -> Gender.MALE;
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
