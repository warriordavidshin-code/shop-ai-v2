package com.petitcamel.shop.member.controller;

import com.petitcamel.shop.member.dto.MemberResponse;
import com.petitcamel.shop.member.dto.PasswordChangeRequest;
import com.petitcamel.shop.member.dto.UpdateMemberRequest;
import com.petitcamel.shop.member.service.MemberService;
import com.petitcamel.shop.member.service.ProfileReauthService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me")
public class MemberController {

    private final MemberService memberService;
    private final ProfileReauthService profileReauthService;

    public MemberController(MemberService memberService, ProfileReauthService profileReauthService) {
        this.memberService = memberService;
        this.profileReauthService = profileReauthService;
    }

    @GetMapping
    public MemberResponse getMe(@AuthenticationPrincipal MemberPrincipal principal) {
        return memberService.getMe(principal.getMemberId());
    }

    @PatchMapping
    public MemberResponse updateMe(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody UpdateMemberRequest request,
            HttpServletRequest httpRequest) {
        profileReauthService.requireVerified(httpRequest, principal.getMemberId());
        return memberService.updateMe(principal.getMemberId(), request);
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest request) {
        memberService.changePassword(principal.getMemberId(), request);
        return ResponseEntity.noContent().build();
    }
}
