package com.petitcamel.shop.auth.oauth;

import com.petitcamel.shop.auth.service.AuthService;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.security.AuthCookieService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/auth")
public class SocialOAuthController {

    private static final Logger log = LoggerFactory.getLogger(SocialOAuthController.class);

    private final OAuthProperties oAuthProperties;
    private final OAuthStateService oAuthStateService;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final NaverOAuthClient naverOAuthClient;
    private final SocialAuthService socialAuthService;
    private final AuthCookieService authCookieService;

    public SocialOAuthController(
            OAuthProperties oAuthProperties,
            OAuthStateService oAuthStateService,
            KakaoOAuthClient kakaoOAuthClient,
            NaverOAuthClient naverOAuthClient,
            SocialAuthService socialAuthService,
            AuthCookieService authCookieService) {
        this.oAuthProperties = oAuthProperties;
        this.oAuthStateService = oAuthStateService;
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.naverOAuthClient = naverOAuthClient;
        this.socialAuthService = socialAuthService;
        this.authCookieService = authCookieService;
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
        try {
            if (error != null && !error.isBlank()) {
                log.warn("OAuth provider returned error provider={} error={}", provider, error);
                return redirectTo(frontendLoginError("oauth_denied"));
            }
            if (code == null || code.isBlank()) {
                return redirectTo(frontendLoginError("oauth_missing_code"));
            }

            String redirectPath = oAuthStateService.consume(state, provider);
            SocialProfile profile = switch (provider) {
                case KAKAO -> kakaoOAuthClient.exchange(code);
                case NAVER -> naverOAuthClient.exchange(code, state);
                default -> throw new IllegalStateException("Unsupported provider: " + provider);
            };

            AuthService.AuthResult result = socialAuthService.loginOrSignup(profile);
            authCookieService.writeAuthCookies(response, result.accessToken(), result.refreshToken());
            return redirectTo(frontendSuccess(redirectPath));
        } catch (BusinessException ex) {
            log.warn("OAuth callback failed provider={} message={}", provider, ex.getMessage());
            return redirectTo(frontendLoginError("oauth_failed"));
        } catch (Exception ex) {
            log.error("Unexpected OAuth callback failure provider={}", provider, ex);
            return redirectTo(frontendLoginError("oauth_failed"));
        }
    }

    private String frontendSuccess(String redirectPath) {
        String path = OAuthStateService.sanitizeRedirect(redirectPath);
        return UriComponentsBuilder.fromUriString(trimSlash(oAuthProperties.getFrontendUrl()))
                .path(path)
                .build(true)
                .toUriString();
    }

    private String frontendLoginError(String code) {
        return UriComponentsBuilder.fromUriString(trimSlash(oAuthProperties.getFrontendUrl()))
                .path("/login")
                .queryParam("error", code)
                .build(true)
                .toUriString();
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
