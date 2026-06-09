package com.ego.raw_ego.auth.security;

import com.ego.raw_ego.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles 401 Unauthorized errors when an unauthenticated request
 * reaches a protected endpoint.
 *
 * <p>Spring Security calls this instead of redirecting to a login page
 * (we are stateless — no session, no login page).
 *
 * <p>Response:
 * <pre>
 * HTTP/1.1 401 Unauthorized
 * Content-Type: application/json
 * {
 *   "success": false,
 *   "message": "Authentication required. Please provide a valid Bearer token.",
 *   "timestamp": "2026-05-21T09:00:00Z"
 * }
 * </pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        log.warn("Unauthorized access to [{}]: {}", request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.error("Authentication required. Please provide a valid Bearer token.")
        );
    }
}
