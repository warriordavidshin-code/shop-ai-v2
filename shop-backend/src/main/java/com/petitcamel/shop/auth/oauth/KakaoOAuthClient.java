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
import java.util.ArrayList;
import java.util.List;

@Component
public class KakaoOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoOAuthClient.class);
    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

    /**
     * Required consent items (카카오 개발자 콘솔에서도 필수 동의로 설정):
     * account_email, name, gender, age_range, birthyear, phone_number
     */
    private static final String KAKAO_SCOPES = String.join(",",
            "account_email",
            "name",
            "gender",
            "age_range",
            "birthyear",
            "birthday",
            "phone_number",
            "profile_nickname",
            "profile_image");

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
                .queryParam("scope", KAKAO_SCOPES)
                // Force consent so required profile fields are collected on first signup.
                .queryParam("prompt", "consent")
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
                textOrNull(account, "name"),
                textOrNull(profile, "nickname"));
        String image = firstNonBlank(
                textOrNull(profile, "profile_image_url"),
                textOrNull(profile, "thumbnail_image_url"));
        String ageRange = textOrNull(account, "age_range");
        String birthyear = textOrNull(account, "birthyear");
        LocalDate birthDate = parseKakaoBirth(birthyear, textOrNull(account, "birthday"), ageRange);
        Gender gender = mapKakaoGender(textOrNull(account, "gender"));
        String phone = normalizeKakaoPhone(textOrNull(account, "phone_number"));

        requireKakaoConsentFields(email, name, gender, birthyear, ageRange, birthDate, phone);

        return new SocialProfile(
                AuthProvider.KAKAO,
                providerUserId,
                email,
                name,
                image,
                birthDate,
                gender,
                phone,
                ageRange);
    }

    private void requireKakaoConsentFields(
            String email,
            String name,
            Gender gender,
            String birthyear,
            String ageRange,
            LocalDate birthDate,
            String phone) {
        List<String> missing = new ArrayList<>();
        if (email == null || email.isBlank()) {
            missing.add("이메일(account_email)");
        }
        if (name == null || name.isBlank()) {
            missing.add("이름(name)");
        }
        if (gender == null || gender == Gender.PREFER_NOT_TO_SAY) {
            missing.add("성별(gender)");
        }
        if ((birthyear == null || birthyear.isBlank()) && (ageRange == null || ageRange.isBlank()) && birthDate == null) {
            missing.add("출생연도/연령대(birthyear, age_range)");
        }
        if (phone == null || phone.isBlank()) {
            missing.add("전화번호(phone_number)");
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "카카오 필수 동의 항목이 부족합니다: " + String.join(", ", missing)
                            + ". 카카오 동의 화면에서 모두 허용한 뒤 다시 시도해 주세요.");
        }
    }

    private void ensureConfigured() {
        if (!isAvailable()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "카카오 로그인이 설정되지 않았습니다.");
        }
    }

    private static LocalDate parseKakaoBirth(String birthyear, String birthday, String ageRange) {
        if (birthyear != null && birthday != null && birthday.length() == 4) {
            try {
                return LocalDate.parse(birthyear + birthday, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        if (birthyear != null && birthyear.matches("\\d{4}")) {
            try {
                return LocalDate.of(Integer.parseInt(birthyear), 1, 1);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return approximateBirthDateFromAgeRange(ageRange);
    }

    /**
     * age_range examples: "20~29", "30~39", "0~9"
     */
    private static LocalDate approximateBirthDateFromAgeRange(String ageRange) {
        if (ageRange == null || ageRange.isBlank()) {
            return null;
        }
        try {
            String normalized = ageRange.replace(" ", "");
            int tilde = normalized.indexOf('~');
            if (tilde <= 0) {
                return null;
            }
            int low = Integer.parseInt(normalized.substring(0, tilde).replaceAll("[^0-9]", ""));
            int year = LocalDate.now().getYear() - low - 5;
            return LocalDate.of(Math.max(year, 1900), 1, 1);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Gender mapKakaoGender(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase()) {
            case "female" -> Gender.FEMALE;
            case "male" -> Gender.MALE;
            default -> null;
        };
    }

    /**
     * Kakao phone_number example: "+82 10-1234-5678" → "01012345678"
     */
    static String normalizeKakaoPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+82")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("82") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        digits = digits.replace("+", "");
        if (digits.length() < 9 || digits.length() > 32) {
            return null;
        }
        return digits;
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
