package com.petitcamel.shop.security;

import com.petitcamel.shop.member.domain.MemberRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class MemberPrincipal implements UserDetails {

    private final Long memberId;
    private final String loginId;
    private final MemberRole role;

    public MemberPrincipal(Long memberId, String loginId, MemberRole role) {
        this.memberId = memberId;
        this.loginId = loginId;
        this.role = role;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getEmail() {
        return loginId;
    }

    public MemberRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
