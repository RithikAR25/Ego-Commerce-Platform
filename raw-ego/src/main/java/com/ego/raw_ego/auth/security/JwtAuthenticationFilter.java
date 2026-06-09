package com.ego.raw_ego.auth.security;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.service.JwtService;
import com.ego.raw_ego.auth.service.TokenBlocklistService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * JWT authentication filter — runs once per request, before Spring Security's
 * authorization layer.
 *
 * <p>Filter chain position:
 * <pre>
 *   CorsFilter
 *       ↓
 *   JwtAuthenticationFilter   ← this class
 *       ↓
 *   UsernamePasswordAuthenticationFilter (skipped — stateless)
 *       ↓
 *   AuthorizationFilter        ← enforces route matchers
 * </pre>
 *
 * <p>Critical guard — {@code passwordChangedAt}:
 * After a password reset, all previously issued access tokens are rejected even
 * if they haven't expired yet. The check: {@code iat < user.passwordChangedAt → reject}.
 *
 * <p>Never throws exceptions — all failures simply leave the SecurityContext empty,
 * which triggers {@link JwtAuthenticationEntryPoint} downstream.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService             jwtService;
    private final UserDetailsService     userDetailsService;
    private final TokenBlocklistService  tokenBlocklistService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // ── No token → skip (public endpoints pass, protected ones fail at AuthorizationFilter)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        // ── Invalid/expired token → skip (entrypoint handles the 401)
        if (!jwtService.isTokenValid(token)) {
            log.debug("Invalid/expired JWT for request: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // ── Blocklist check — reject tokens explicitly revoked at logout
        // Checked AFTER signature validation to avoid Redis calls for malformed tokens.
        if (tokenBlocklistService.isBlocked(token)) {
            log.debug("Blocklisted JWT rejected for request: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims  claims  = jwtService.extractAllClaims(token);
            String  email   = claims.getSubject();
            Instant issuedAt = claims.getIssuedAt() != null
                    ? claims.getIssuedAt().toInstant()
                    : Instant.EPOCH;

            // ── Only set context if not already authenticated (avoid double-processing)
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // ── passwordChangedAt guard — invalidate stale tokens after password reset
                if (userDetails instanceof User user && user.getPasswordChangedAt() != null) {
                    if (issuedAt.isBefore(user.getPasswordChangedAt())) {
                        log.warn("JWT issued before password change — rejecting token for user={}",
                                email);
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                // ── All checks passed — authenticate the request
                if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null, // credentials null = already authenticated via JWT
                                    userDetails.getAuthorities()
                            );
                    // Note: setDetails() via WebAuthenticationDetailsSource is deprecated in
                    // Spring Security 7 and not needed for stateless JWT — omitted intentionally.
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            // ── Log and continue — let the authorization layer reject the request
            log.warn("JWT processing error for request {}: {}", request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
