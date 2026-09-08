package com.petitcamel.shop.auth.controller;

import com.petitcamel.shop.auth.dto.LoginRequest;
import com.petitcamel.shop.auth.dto.SignupRequest;
import com.petitcamel.shop.auth.service.AuthService;
import com.petitcamel.shop.member.dto.MemberResponse;
import com.petitcamel.shop.security.AuthCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/signup")
    public ResponseEntity<MemberResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletResponse response) {
        AuthService.AuthResult result = authService.signup(request);
        authCookieService.writeAuthCookies(response, result.accessToken(), result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.member());
    }

    @PostMapping("/login")
    public ResponseEntity<MemberResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(request);
        authCookieService.writeAuthCookies(response, result.accessToken(), result.refreshToken());
        return ResponseEntity.ok(result.member());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieService
                .readCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE)
                .orElse(null);
        AuthService.AuthResult result = authService.refresh(refreshToken);
        authCookieService.writeAuthCookies(response, result.accessToken(), result.refreshToken());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieService
                .readCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE)
                .orElse(null);
        authService.logout(refreshToken);
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }
}
