package com.petitcamel.shop.member.controller;

import com.petitcamel.shop.member.domain.AuthProvider;
import com.petitcamel.shop.member.dto.ReauthPasswordRequest;
import com.petitcamel.shop.member.dto.ReauthStatusResponse;
import com.petitcamel.shop.member.service.ProfileReauthService;
import com.petitcamel.shop.member.service.ProfileReauthService.ReauthToken;
import com.petitcamel.shop.security.AuthCookieService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/reauth")
public class ProfileReauthController {

    private final ProfileReauthService profileReauthService;
    private final AuthCookieService authCookieService;

    public ProfileReauthController(ProfileReauthService profileReauthService, AuthCookieService authCookieService) {
        this.profileReauthService = profileReauthService;
        this.authCookieService = authCookieService;
    }

    @GetMapping
    public ReauthStatusResponse status(
            @AuthenticationPrincipal MemberPrincipal principal,
            HttpServletRequest request) {
        return profileReauthService.status(request, principal.getMemberId());
    }

    @PostMapping
    public ReauthStatusResponse verifyPassword(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ReauthPasswordRequest body,
            HttpServletResponse response) {
        ReauthToken token = profileReauthService.verifyPassword(principal.getMemberId(), body.password());
        authCookieService.writeReauthCookie(response, token.token(), token.maxAge());
        return new ReauthStatusResponse(true, token.expiresAt(), AuthProvider.LOCAL);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(HttpServletResponse response) {
        authCookieService.clearReauthCookie(response);
        return ResponseEntity.noContent().build();
    }
}
