package com.petitcamel.shop.admin.controller;

import com.petitcamel.shop.admin.dto.AdminMemberResponse;
import com.petitcamel.shop.admin.dto.MemberStatusUpdateRequest;
import com.petitcamel.shop.common.dto.PageResponse;
import com.petitcamel.shop.member.service.MemberService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final MemberService memberService;

    public AdminMemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public PageResponse<AdminMemberResponse> listMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return memberService.listAdminMembers(page, size);
    }

    @PatchMapping("/{memberId}/status")
    public AdminMemberResponse updateStatus(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberStatusUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        Long actorId = principal == null ? null : principal.getMemberId();
        return memberService.updateAdminStatus(memberId, request.status(), actorId);
    }
}
