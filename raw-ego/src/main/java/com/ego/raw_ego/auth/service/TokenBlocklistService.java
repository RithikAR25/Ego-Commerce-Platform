package com.ego.raw_ego.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Redis-backed access token blocklist — implements immediate AT invalidation on logout.
 *
 * <h3>The Gap It Closes</h3>
 * <p>Without this service, logging out only revokes the refresh token. The access
 * token remains cryptographically valid until its natural expiry (~15 min). A stolen
 * AT would remain usable for the full TTL window even after the user logs out.
 *
 * <h3>How It Works</h3>
 * <p>On logout, the raw access token is stored in Redis under the key:
 * {@code "blocklist:at:{rawToken}"} with a TTL equal to the token's remaining
 * validity time. When the token expires naturally, Redis auto-evicts the entry —
 * no cleanup job needed.
 *
 * <p>On every authenticated request, {@link com.ego.raw_ego.auth.security.JwtAuthenticationFilter}
 * checks this blocklist AFTER signature validation and BEFORE setting the
 * security context. A hit = reject the token silently (let the request fail
 * at the AuthorizationFilter with 401).
 *
 * <h3>Storage Key Format</h3>
 * <pre>
 *   "blocklist:at:{rawToken}"
 *   TTL = remaining seconds until exp claim
 *   Value = "revoked" (arbitrary — only the key's existence matters)
 * </pre>
 *
 * <h3>Infrastructure</h3>
 * <p>Uses {@link StringRedisTemplate} which is already configured by
 * {@code RedisConfig} (Lettuce driver). No additional bean configuration needed.
 *
 * <h3>Trade-offs</h3>
 * <ul>
 *   <li>One Redis read per authenticated request (after signature validation).</li>
 *   <li>With 15-min AT TTL, blocklist entries auto-expire quickly — minimal memory overhead.</li>
 *   <li>If Redis is unavailable, {@link #block} fails silently (logs WARN) to prevent logout
 *       from breaking. {@link #isBlocked} also fails safe to {@code false} — meaning if Redis
 *       is down, the blocklist check passes, and the token remains usable. This is an acceptable
 *       tradeoff: Redis unavailability should not lock legitimate users out of the system.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlocklistService {

    private static final String KEY_PREFIX = "blocklist:at:";

    private final StringRedisTemplate redisTemplate;
    private final JwtService jwtService;

    // ── Block (add to blocklist on logout) ───────────────────────────────────

    /**
     * Adds the given access token to the Redis blocklist.
     *
     * <p>The entry TTL is set to the token's remaining validity so it auto-evicts
     * exactly when the token would have naturally expired. Entries are never stored
     * indefinitely — Redis handles cleanup automatically.
     *
     * @param rawAccessToken the raw JWT string from the {@code Authorization: Bearer} header
     */
    public void block(String rawAccessToken) {
        try {
            Date expiry = jwtService.extractAllClaims(rawAccessToken).getExpiration();
            if (expiry == null) {
                log.warn("[TokenBlocklist] Access token has no expiry claim — skip blocklist");
                return;
            }

            long remainingSeconds = Math.max(0,
                    Duration.between(Instant.now(), expiry.toInstant()).getSeconds());

            if (remainingSeconds <= 0) {
                // Token is already expired — no need to blocklist it
                log.debug("[TokenBlocklist] Token already expired — skip blocklist");
                return;
            }

            String key = KEY_PREFIX + rawAccessToken;
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofSeconds(remainingSeconds));
            log.debug("[TokenBlocklist] Access token blocked. TTL={}s", remainingSeconds);

        } catch (Exception e) {
            // Non-fatal — the RT has already been revoked. Log but don't break logout.
            log.warn("[TokenBlocklist] Failed to blocklist access token: {}", e.getMessage());
        }
    }

    // ── Check (per-request validation) ───────────────────────────────────────

    /**
     * Returns {@code true} if the given access token is present in the blocklist.
     *
     * <p>A {@code true} result means the token was explicitly revoked (e.g. after logout).
     * Called by {@link com.ego.raw_ego.auth.security.JwtAuthenticationFilter} on every
     * authenticated request, after signature validation.
     *
     * <p>Fails safe to {@code false} on Redis errors — logged as WARN.
     *
     * @param rawAccessToken the raw JWT string
     * @return {@code true} if blocked, {@code false} otherwise (including on Redis failure)
     */
    public boolean isBlocked(String rawAccessToken) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(KEY_PREFIX + rawAccessToken));
        } catch (Exception e) {
            log.warn("[TokenBlocklist] Redis unavailable during blocklist check — fail safe (allow): {}", e.getMessage());
            return false; // Fail open: don't lock out users if Redis is down
        }
    }
}
