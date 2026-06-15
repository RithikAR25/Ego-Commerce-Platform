package com.ego.raw_ego.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Implementation of {@link TokenBlocklistService} — Redis-backed access token blocklist.
 *
 * <h3>Storage Key Format</h3>
 * <pre>
 *   "blocklist:at:{rawToken}"
 *   TTL = remaining seconds until exp claim
 *   Value = "revoked" (arbitrary — only the key's existence matters)
 * </pre>
 *
 * <h3>Trade-offs</h3>
 * <ul>
 *   <li>One Redis read per authenticated request (after signature validation).</li>
 *   <li>With 15-min AT TTL, blocklist entries auto-expire quickly — minimal memory overhead.</li>
 *   <li>If Redis is unavailable, {@link #block} fails silently; {@link #isBlocked} fails safe
 *       to {@code false} — meaning the token remains usable. Acceptable tradeoff.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlocklistServiceImpl implements TokenBlocklistService {

    private static final String KEY_PREFIX = "blocklist:at:";

    private final StringRedisTemplate redisTemplate;
    private final JwtService          jwtService;

    @Override
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
                log.debug("[TokenBlocklist] Token already expired — skip blocklist");
                return;
            }

            String key = KEY_PREFIX + rawAccessToken;
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofSeconds(remainingSeconds));
            log.debug("[TokenBlocklist] Access token blocked. TTL={}s", remainingSeconds);

        } catch (Exception e) {
            log.warn("[TokenBlocklist] Failed to blocklist access token: {}", e.getMessage());
        }
    }

    @Override
    public boolean isBlocked(String rawAccessToken) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(KEY_PREFIX + rawAccessToken));
        } catch (Exception e) {
            log.warn("[TokenBlocklist] Redis unavailable during blocklist check — fail safe (allow): {}", e.getMessage());
            return false;
        }
    }
}
