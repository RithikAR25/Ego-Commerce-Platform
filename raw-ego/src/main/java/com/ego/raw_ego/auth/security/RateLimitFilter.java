package com.ego.raw_ego.auth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiter for sensitive authentication endpoints.
 *
 * <h3>Protected endpoints and limits</h3>
 * <ul>
 *   <li>{@code POST /api/v1/auth/login}              — 5 attempts per 60 seconds per IP</li>
 *   <li>{@code POST /api/v1/auth/register}            — 3 attempts per 60 seconds per IP</li>
 *   <li>{@code POST /api/v1/auth/resend-verification} — 3 attempts per 60 minutes per IP</li>
 * </ul>
 *
 * <h3>Implementation</h3>
 * <p>Uses Bucket4j with an in-memory {@link ConcurrentHashMap} keyed by
 * {@code (IP + endpoint)} for per-endpoint isolation. No external infrastructure
 * (Redis, database) is required — the buckets live in JVM heap.
 *
 * <p>Trade-off: In-memory buckets are per-instance only. If the application scales
 * horizontally to multiple JVM instances, rate limits will be per-pod rather than
 * global. For a single-instance deployment (Railway free tier), this is acceptable.
 * Upgrade path: replace the {@code ConcurrentHashMap} with a
 * {@code BucketProxyManager} backed by Upstash Redis for distributed limiting.
 *
 * <h3>Response on rate limit exceeded</h3>
 * Returns HTTP {@code 429 Too Many Requests} with a JSON body consistent with
 * the {@link com.ego.raw_ego.common.dto.ApiResponse} envelope used by all other
 * error responses in the EGO API.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Separate buckets per (IP + endpoint path) to prevent one endpoint's
    // exhaustion from blocking the other.
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final String LOGIN_PATH               = "/api/v1/auth/login";
    private static final String REGISTER_PATH             = "/api/v1/auth/register";
    private static final String RESEND_VERIFICATION_PATH = "/api/v1/auth/resend-verification";

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        String path   = request.getRequestURI();
        String method = request.getMethod();

        // Only apply rate limiting to POST requests on protected auth endpoints
        if ("POST".equalsIgnoreCase(method)
                && (LOGIN_PATH.equals(path)
                        || REGISTER_PATH.equals(path)
                        || RESEND_VERIFICATION_PATH.equals(path))) {

            String clientIp  = resolveClientIp(request);
            String bucketKey = clientIp + "::" + path;
            Bucket bucket    = buckets.computeIfAbsent(bucketKey, k -> buildBucket(path));

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded: ip={} path={}", clientIp, path);
                writeTooManyRequestsResponse(response, path);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds a Bucket4j token-bucket with limits appropriate for the given path.
     *
     * <ul>
     *   <li>Login:               5 tokens, refill 5 per 60 seconds</li>
     *   <li>Register:            3 tokens, refill 3 per 60 seconds</li>
     *   <li>Resend-verification: 3 tokens, refill 3 per 60 minutes</li>
     * </ul>
     */
    private Bucket buildBucket(String path) {
        Bandwidth bandwidth;
        if (LOGIN_PATH.equals(path)) {
            bandwidth = Bandwidth.simple(5, Duration.ofSeconds(60));    // 5 logins / min
        } else if (RESEND_VERIFICATION_PATH.equals(path)) {
            bandwidth = Bandwidth.simple(3, Duration.ofMinutes(60));    // 3 resend-verif / hour
        } else {
            bandwidth = Bandwidth.simple(3, Duration.ofSeconds(60));    // 3 registrations / min
        }

        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Resolves the real client IP, respecting X-Forwarded-For for clients
     * behind load balancers and reverse proxies (Railway, Vercel).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take only the first IP (leftmost = originating client)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Writes a 429 response using the same JSON envelope structure as
     * {@link com.ego.raw_ego.common.exception.GlobalExceptionHandler}.
     */
    private void writeTooManyRequestsResponse(HttpServletResponse response, String path) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String message;
        if (LOGIN_PATH.equals(path)) {
            message = "Too many login attempts. Please wait 60 seconds before trying again.";
        } else if (RESEND_VERIFICATION_PATH.equals(path)) {
            message = "Too many verification email requests. Please wait 60 minutes before trying again.";
        } else {
            message = "Too many registration attempts. Please wait 60 seconds before trying again.";
        }

        String json = """
                {
                  "success": false,
                  "message": "%s",
                  "data": null,
                  "errors": null
                }
                """.formatted(message);

        response.getWriter().write(json);
    }
}
