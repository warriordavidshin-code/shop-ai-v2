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
    private static final String SHIPPING_ADDRESS_URL = "https://kapi.kakao.com/v1/user/shipping_address";

    /**
     * Only request scopes that are enabled in Kakao Developers > 카카오 로그인 > 동의항목.
     * Required consent: account_email, name, gender, age_range, birthyear, phone_number, shipping_address
     * Do NOT include birthday / profile_nickname / profile_image unless those items are also enabled there.
     */
    private static final String KAKAO_SCOPES = String.join(",",
            "account_email",
            "name",
            "gender",
            "age_range",
            "birthyear",
            "phone_number",
            "shipping_address");

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

        ShippingAddress shipping = fetchShippingAddress(accessToken);
        name = firstNonBlank(name, shipping.receiverName());
        phone = firstNonBlank(phone, shipping.receiverPhone());

        requireKakaoConsentFields(
                email,
                name,
                gender,
                birthyear,
                ageRange,
                birthDate,
                phone,
                shipping);

        return new SocialProfile(
                AuthProvider.KAKAO,
                providerUserId,
                email,
                name,
                image,
                birthDate,
                gender,
                phone,
                ageRange,
                shipping.postcode(),
                shipping.address1(),
                shipping.address2());
    }

    private ShippingAddress fetchShippingAddress(String accessToken) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(SHIPPING_ADDRESS_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("Kakao shipping address fetch failed: {}", ex.getMessage());
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "카카오 필수 동의 항목이 부족합니다: 배송지정보(shipping_address). 카카오 동의 화면에서 모두 허용한 뒤 다시 시도해 주세요.");
        }
        if (response == null) {
            return ShippingAddress.empty();
        }
        if (response.path("shipping_addresses_needs_agreement").asBoolean(false)) {
            return ShippingAddress.empty();
        }
        JsonNode addresses = response.path("shipping_addresses");
        if (!addresses.isArray() || addresses.isEmpty()) {
            return ShippingAddress.empty();
        }

        JsonNode selected = null;
        for (JsonNode address : addresses) {
            if (address.path("is_default").asBoolean(false)) {
                selected = address;
                break;
            }
        }
        if (selected == null) {
            selected = addresses.get(0);
        }
        return ShippingAddress.from(selected);
    }

    private void requireKakaoConsentFields(
            String email,
            String name,
            Gender gender,
            String birthyear,
            String ageRange,
            LocalDate birthDate,
            String phone,
            ShippingAddress shipping) {
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
        if (!shipping.isPresent()) {
            missing.add("배송지정보(shipping_address)");
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

    record ShippingAddress(
            String receiverName,
            String receiverPhone,
            String postcode,
            String address1,
            String address2
    ) {
        static ShippingAddress empty() {
            return new ShippingAddress(null, null, null, null, null);
        }

        static ShippingAddress from(JsonNode address) {
            String postcode = firstNonBlank(
                    textOrNull(address, "zone_number"),
                    textOrNull(address, "zip_code"));
            return new ShippingAddress(
                    textOrNull(address, "receiver_name"),
                    normalizeKakaoPhone(firstNonBlank(
                            textOrNull(address, "receiver_phone_number1"),
                            textOrNull(address, "receiver_phone_number2"))),
                    postcode,
                    textOrNull(address, "base_address"),
                    textOrNull(address, "detail_address"));
        }

        boolean isPresent() {
            return (address1 != null && !address1.isBlank())
                    || (postcode != null && !postcode.isBlank());
        }
    }
}
