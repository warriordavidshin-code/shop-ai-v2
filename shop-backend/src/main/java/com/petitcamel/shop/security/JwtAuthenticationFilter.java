package com.petitcamel.shop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthCookieService authCookieService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthCookieService authCookieService) {
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            resolveAccessToken(request).ifPresent(token -> {
                try {
                    JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
                    MemberPrincipal principal = new MemberPrincipal(
                            claims.memberId(), claims.loginId(), claims.role());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (JwtService.InvalidTokenException ignored) {
                    // Leave unauthenticated; protected endpoints will return 401.
                }
            });
        }
        filterChain.doFilter(request, response);
    }

    private java.util.Optional<String> resolveAccessToken(HttpServletRequest request) {
        return authCookieService.readCookie(request, AuthCookieService.ACCESS_TOKEN_COOKIE)
                .or(() -> {
                    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (header != null && header.startsWith("Bearer ")) {
                        return java.util.Optional.of(header.substring(7).trim());
                    }
                    return java.util.Optional.empty();
                });
    }
}
