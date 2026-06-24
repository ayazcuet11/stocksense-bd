package com.stocksense.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.nonNull;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request
            , HttpServletResponse response
            , FilterChain chain) throws ServletException, IOException {

        var header = request.getHeader("Authorization");

        if (nonNull(header) && header.startsWith("Bearer ")) {

            var token = header.substring(7);

            if (tokenProvider.isValid(token)) {
                var claims = tokenProvider.parse(token);
                var role = claims.get("role", String.class);
                var tenantId = claims.get("tenantId", Long.class);

                TenantContext.set(tenantId);

                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject()
                        , null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(auth);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
