package com.petitcamel.shop.member.controller;

import com.petitcamel.shop.member.dto.MemberAddressRequest;
import com.petitcamel.shop.member.dto.MemberAddressResponse;
import com.petitcamel.shop.member.service.MemberAddressService;
import com.petitcamel.shop.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members/me/addresses")
public class MemberAddressController {

    private final MemberAddressService memberAddressService;

    public MemberAddressController(MemberAddressService memberAddressService) {
        this.memberAddressService = memberAddressService;
    }

    @GetMapping
    public List<MemberAddressResponse> list(@AuthenticationPrincipal MemberPrincipal principal) {
        return memberAddressService.list(principal.getMemberId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberAddressResponse create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody MemberAddressRequest request) {
        return memberAddressService.create(principal.getMemberId(), request);
    }

    @PutMapping("/{addressId}")
    public MemberAddressResponse update(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long addressId,
            @Valid @RequestBody MemberAddressRequest request) {
        return memberAddressService.update(principal.getMemberId(), addressId, request);
    }

    @PostMapping("/{addressId}/default")
    public MemberAddressResponse setDefault(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long addressId) {
        return memberAddressService.setDefault(principal.getMemberId(), addressId);
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long addressId) {
        memberAddressService.delete(principal.getMemberId(), addressId);
        return ResponseEntity.noContent().build();
    }
}
