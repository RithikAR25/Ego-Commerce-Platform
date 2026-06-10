package com.ego.raw_ego.auth.service;

import com.ego.raw_ego.auth.entity.RefreshToken;
import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.repository.RefreshTokenRepository;
import com.ego.raw_ego.common.exception.AuthException;
import com.ego.raw_ego.common.util.LogMasker;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages the full lifecycle of refresh tokens:
 * create → rotate → revoke (single) → revoke (family) → revoke (all user).
 *
 * <p>Security model:
 * <ul>
 *   <li><b>Raw tokens are UUID strings</b> — never stored. Only their SHA-256 hash lives in DB.</li>
 *   <li><b>Token rotation</b> — every successful /refresh call revokes the submitted token
 *       and issues a new one with the same family_id.</li>
 *   <li><b>Reuse detection</b> — if a client submits an already-revoked token, the entire
 *       family is revoked immediately (signals token theft).</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${spring.jwt.refresh-token-expiry-days:30}")
    private long refreshTokenExpiryDays;

    /**
     * Maximum number of simultaneously active sessions (distinct login families) per user.
     * When this limit is reached, the oldest session is evicted before creating the new one.
     * Configurable via {@code ego.auth.max-sessions-per-user} (default 5).
     */
    @Value("${ego.auth.max-sessions-per-user:5}")
    private int maxSessionsPerUser;

    // ── Create ─────────────────────────────────────────────────────────────────

    /**
     * Creates a new refresh token for the given user.
     *
     * @param user       the authenticated user
     * @param familyId   NULL on first login (new UUID assigned); pass existing on rotation
     * @param request    used to capture device hint and IP for session management display
     * @return the RAW token UUID — send to client, never store raw
     */
    @Transactional
    public String createRefreshToken(User user, String familyId, HttpServletRequest request) {
        String rawToken       = UUID.randomUUID().toString();
        String tokenHash      = hashToken(rawToken);
        String resolvedFamily = (familyId != null) ? familyId : UUID.randomUUID().toString();

        // ── Session cap enforcement (applies only on fresh logins, not on rotations) ──
        // familyId == null means this is a brand-new session (login / register).
        // On rotation, the caller passes the existing familyId, so we skip the check.
        if (familyId == null) {
            enforceSessionLimit(user);
        }

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .familyId(resolvedFamily)
                .deviceHint(extractDeviceHint(request))
                .ipAddress(extractIpAddress(request))
                .expiresAt(Instant.now().plusSeconds(refreshTokenExpiryDays * 24L * 60 * 60))
                .build();

        refreshTokenRepository.save(refreshToken);
        log.debug("Refresh token created for userId={} family={}", user.getId(), resolvedFamily);
        return rawToken; // Return raw — service stores only the hash
    }

    // ── Rotate ─────────────────────────────────────────────────────────────────

    /**
     * Validates a submitted refresh token and revokes it in preparation for rotation.
     *
     * <p>Call flow:
     * <ol>
     *   <li>Hash the raw token → look up in DB</li>
     *   <li>If not found → invalid (never existed or cleaned up)</li>
     *   <li>If already revoked → REUSE DETECTED → revoke entire family → throw 401</li>
     *   <li>If expired → throw 401</li>
     *   <li>Otherwise → revoke this token and return it (caller creates replacement)</li>
     * </ol>
     *
     * @param rawToken the raw UUID from the client
     * @return the now-revoked {@link RefreshToken} entity (caller uses family_id for new token)
     */
    @Transactional
    public RefreshToken rotateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh token lookup failed — hash not found in DB");
                    return new AuthException("Invalid refresh token. Please log in again.");
                });

        if (existing.isRevoked()) {
            // ── REUSE DETECTED — possible token theft ──────────────────────────
            log.error("SECURITY ALERT: Refresh token reuse detected for family={}. " +
                    "Revoking entire session family as theft countermeasure.",
                    existing.getFamilyId());
            int revoked = refreshTokenRepository.revokeAllByFamilyId(
                    existing.getFamilyId(), Instant.now());
            log.warn("Revoked {} token(s) in family {} as theft countermeasure.",
                    revoked, existing.getFamilyId());
            throw new AuthException(
                    "Session security violation detected. All sessions have been terminated. Please log in again.");
        }

        if (existing.isExpired()) {
            log.info("Refresh token expired for email={}", LogMasker.maskEmail(existing.getUser().getEmail()));
            throw new AuthException("Session expired. Please log in again.");
        }

        // Revoke the submitted token (it gets replaced by the new one the caller creates)
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);
        return existing;
    }

    // ── Revoke single ──────────────────────────────────────────────────────────

    /**
     * Revokes a single refresh token by raw value. Silently ignores unknown tokens.
     * Used by the logout endpoint.
     */
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresentOrElse(
                rt -> {
                    rt.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(rt);
                    log.info("Refresh token revoked (logout) for email={}",
                            LogMasker.maskEmail(rt.getUser().getEmail()));
                },
                () -> log.debug("Logout: refresh token not found in DB — may have already been cleaned up")
        );
    }

    // ── Revoke all (user) ──────────────────────────────────────────────────────

    /**
     * Revokes ALL active refresh tokens for a user.
     * Called on: password reset, account suspension, admin force-logout.
     */
    @Transactional
    public void revokeAllUserTokens(Long userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("Revoked {} refresh token(s) for userId={}", count, userId);
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    /**
     * Enforces the per-user session cap.
     *
     * <p>Counts distinct active refresh token families for the user. If the count is
     * already at or above {@link #maxSessionsPerUser}, the oldest active family is
     * revoked entirely (all its tokens), making room for the new session being created.
     *
     * <p>This matches the behaviour of Google Accounts and Netflix — the oldest device
     * is silently signed out when the maximum concurrent session limit is reached.
     *
     * @param user the user about to receive a new session token
     */
    private void enforceSessionLimit(User user) {
        Instant now = Instant.now();
        long activeSessions = refreshTokenRepository.countActiveSessionsByUserId(user.getId(), now);

        if (activeSessions >= maxSessionsPerUser) {
            // Find the oldest active session token and revoke its entire family
            refreshTokenRepository
                    .findActiveSessionsOldestFirst(user.getId(), now)
                    .stream()
                    .findFirst()
                    .ifPresent(oldest -> {
                        int revoked = refreshTokenRepository.revokeAllByFamilyId(
                                oldest.getFamilyId(), now);
                        log.info("Session cap reached for userId={}: evicted oldest family={} ({} token(s) revoked). " +
                                 "Active sessions={}, max={}.",
                                user.getId(), oldest.getFamilyId(), revoked, activeSessions, maxSessionsPerUser);
                    });
        }
    }

    /**
     * Computes SHA-256 of the raw token. Used for storage and lookup.
     * MessageDigest is not thread-safe — obtain a new instance each call.
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JCA spec — this can only happen in a broken JVM
            throw new IllegalStateException("SHA-256 algorithm not available on this JVM", e);
        }
    }

    private String extractDeviceHint(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        return (ua != null && ua.length() > 255) ? ua.substring(0, 255) : ua;
    }

    private String extractIpAddress(HttpServletRequest request) {
        if (request == null) return null;
        // Respect X-Forwarded-For for clients behind load balancers / proxies
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
