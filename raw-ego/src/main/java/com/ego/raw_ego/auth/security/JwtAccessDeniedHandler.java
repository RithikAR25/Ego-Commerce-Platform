package com.ego.raw_ego.auth.security;

import com.ego.raw_ego.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles 403 Forbidden errors when an authenticated user (valid JWT)
 * attempts to access a resource they don't have the role for.
 *
 * <p>Distinct from {@link JwtAuthenticationEntryPoint}:
 * <ul>
 *   <li>401 = not authenticated at all (no/invalid token)</li>
 *   <li>403 = authenticated but insufficient role (e.g. CUSTOMER hitting /admin/**)</li>
 * </ul>
 *
 * <p>Response:
 * <pre>
 * HTTP/1.1 403 Forbidden
 * Content-Type: application/json
 * {
 *   "success": false,
 *   "message": "Access denied. You do not have permission to access this resource.",
 *   "timestamp": "2026-05-21T09:00:00Z"
 * }
 * </pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        log.warn("Access denied to [{}] for principal [{}]",
                request.getRequestURI(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "unknown");

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.error("Access denied. You do not have permission to access this resource.")
        );
    }
}
