package com.petitcamel.shop.auth.oauth;

import com.petitcamel.shop.auth.service.AuthService;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.service.ProfileReauthService;
import com.petitcamel.shop.security.AuthCookieService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/auth")
public class SocialOAuthController {

    private static final Logger log = LoggerFactory.getLogger(SocialOAuthController.class);
    private static final String REAUTH_PAGE = "/mypage/verify";

    private final OAuthProperties oAuthProperties;
    private final OAuthStateService oAuthStateService;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final NaverOAuthClient naverOAuthClient;
    private final SocialAuthService socialAuthService;
    private final AuthCookieService authCookieService;
    private final ProfileReauthService profileReauthService;

    public SocialOAuthController(
            OAuthProperties oAuthProperties,
            OAuthStateService oAuthStateService,
            KakaoOAuthClient kakaoOAuthClient,
            NaverOAuthClient naverOAuthClient,
            SocialAuthService socialAuthService,
            AuthCookieService authCookieService,
            ProfileReauthService profileReauthService) {
        this.oAuthProperties = oAuthProperties;
        this.oAuthStateService = oAuthStateService;
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.naverOAuthClient = naverOAuthClient;
        this.socialAuthService = socialAuthService;
        this.authCookieService = authCookieService;
        this.profileReauthService = profileReauthService;
    }

    @GetMapping("/kakao/login")
    public ResponseEntity<Void> kakaoLogin(@RequestParam(value = "redirect", required = false) String redirect) {
        String state = oAuthStateService.issue(AuthProvider.KAKAO, redirect);
        return redirectTo(kakaoOAuthClient.buildAuthorizeUrl(state));
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse response) {
        return handleCallback(AuthProvider.KAKAO, code, state, error, response);
    }

    @GetMapping("/naver/login")
    public ResponseEntity<Void> naverLogin(@RequestParam(value = "redirect", required = false) String redirect) {
        String state = oAuthStateService.issue(AuthProvider.NAVER, redirect);
        return redirectTo(naverOAuthClient.buildAuthorizeUrl(state));
    }

    @GetMapping("/kakao/reauth")
    public ResponseEntity<Void> kakaoReauth(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(value = "redirect", required = false) String redirect) {
        if (principal == null) {
            return redirectTo(frontendPath("/login", "next", REAUTH_PAGE));
        }
        String state = oAuthStateService.issueReauth(AuthProvider.KAKAO, principal.getMemberId(), redirect);
        return redirectTo(kakaoOAuthClient.buildReauthorizeUrl(state));
    }

    @GetMapping("/naver/reauth")
    public ResponseEntity<Void> naverReauth(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(value = "redirect", required = false) String redirect) {
        if (principal == null) {
            return redirectTo(frontendPath("/login", "next", REAUTH_PAGE));
        }
        String state = oAuthStateService.issueReauth(AuthProvider.NAVER, principal.getMemberId(), redirect);
        return redirectTo(naverOAuthClient.buildReauthorizeUrl(state));
    }

    @GetMapping("/naver/callback")
    public ResponseEntity<Void> naverCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse response) {
        return handleCallback(AuthProvider.NAVER, code, state, error, response);
    }

    private ResponseEntity<Void> handleCallback(
            AuthProvider provider,
            String code,
            String state,
            String error,
            HttpServletResponse response) {
        OAuthStateService.OAuthState oauthState = null;
        try {
            if (error != null && !error.isBlank()) {
                log.warn("OAuth provider returned error provider={} error={}", provider, error);
                oauthState = consumeQuietly(state, provider);
                return redirectTo(frontendError(oauthState, "oauth_denied"));
            }
            if (code == null || code.isBlank()) {
                oauthState = consumeQuietly(state, provider);
                return redirectTo(frontendError(oauthState, "oauth_missing_code"));
            }

            oauthState = oAuthStateService.consume(state, provider);
            SocialProfile profile = switch (provider) {
                case KAKAO -> kakaoOAuthClient.exchange(code);
                case NAVER -> naverOAuthClient.exchange(code, state);
                default -> throw new IllegalStateException("Unsupported provider: " + provider);
            };

            if (oauthState.purpose() == OAuthStateService.Purpose.REAUTH) {
                ProfileReauthService.ReauthToken token =
                        profileReauthService.verifySocial(oauthState.memberId(), provider, profile.providerUserId());
                authCookieService.writeReauthCookie(response, token.token(), token.maxAge());
                return redirectTo(frontendPath(OAuthStateService.sanitizeRedirect(oauthState.redirectPath()), null, null));
            }

            SocialAuthService.SocialAuthOutcome outcome = socialAuthService.loginOrSignup(profile);
            AuthService.AuthResult result = outcome.authResult();
            authCookieService.writeAuthCookies(response, result.accessToken(), result.refreshToken());
            return redirectTo(frontendSuccess(oauthState.redirectPath(), outcome.newlyRegistered(), provider));
        } catch (BusinessException ex) {
            log.warn("OAuth callback failed provider={} message={}", provider, ex.getMessage());
            return redirectTo(frontendError(oauthState, mapOAuthErrorCode(ex)));
        } catch (Exception ex) {
            log.error("Unexpected OAuth callback failure provider={}", provider, ex);
            return redirectTo(frontendError(oauthState, "oauth_failed"));
        }
    }

    private OAuthStateService.OAuthState consumeQuietly(String state, AuthProvider provider) {
        try {
            return oAuthStateService.consume(state, provider);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private String frontendError(OAuthStateService.OAuthState oauthState, String code) {
        if (oauthState != null && oauthState.purpose() == OAuthStateService.Purpose.REAUTH) {
            String reauthCode = switch (code) {
                case "oauth_denied" -> "reauth_denied";
                case "oauth_not_configured" -> "oauth_not_configured";
                default -> "reauth_failed";
            };
            return frontendPath(REAUTH_PAGE, "error", reauthCode);
        }
        return frontendLoginError(code);
    }

    private String frontendPath(String path, String queryName, String queryValue) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimSlash(oAuthProperties.getFrontendUrl()))
                .path(path);
        if (queryName != null) {
            builder.queryParam(queryName, queryValue);
        }
        return builder.build().encode().toUriString();
    }

    private String frontendSuccess(String redirectPath, boolean newlyRegistered, AuthProvider provider) {
        String path = OAuthStateService.sanitizeRedirect(redirectPath);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimSlash(oAuthProperties.getFrontendUrl()))
                .path(path);
        if (newlyRegistered) {
            builder.queryParam("social_signup", provider.name().toLowerCase());
        }
        return builder.build(true).toUriString();
    }

    private String frontendLoginError(String code) {
        return UriComponentsBuilder.fromUriString(trimSlash(oAuthProperties.getFrontendUrl()))
                .path("/login")
                .queryParam("error", code)
                .build(true)
                .toUriString();
    }

    private static String mapOAuthErrorCode(BusinessException ex) {
        return switch (ex.getCode()) {
            case CONFLICT -> "oauth_email_conflict";
            case BUSINESS_RULE_VIOLATION -> {
                String message = ex.getMessage() == null ? "" : ex.getMessage();
                if (message.contains("필수 동의") || message.contains("이메일 동의")) {
                    yield "oauth_consent_required";
                }
                if (message.contains("설정되지 않았습니다")) {
                    yield "oauth_not_configured";
                }
                yield "oauth_failed";
            }
            default -> "oauth_failed";
        };
    }

    private static ResponseEntity<Void> redirectTo(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:3000";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
