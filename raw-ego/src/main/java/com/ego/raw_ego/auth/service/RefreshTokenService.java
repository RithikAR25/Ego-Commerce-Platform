package com.ego.raw_ego.auth.service;

import com.ego.raw_ego.auth.entity.RefreshToken;
import com.ego.raw_ego.auth.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Contract for refresh token lifecycle management:
 * create → rotate → revoke (single) → revoke (family) → revoke (all user).
 *
 * <p>Security model:
 * <ul>
 *   <li>Raw tokens are UUID strings — never stored. Only their SHA-256 hash lives in DB.</li>
 *   <li>Token rotation — every successful /refresh call revokes the submitted token
 *       and issues a new one with the same family_id.</li>
 *   <li>Reuse detection — if a client submits an already-revoked token, the entire
 *       family is revoked immediately (signals token theft).</li>
 * </ul>
 */
public interface RefreshTokenService {

    /**
     * Creates a new refresh token for the given user.
     *
     * @param user       the authenticated user
     * @param familyId   NULL on first login (new UUID assigned); pass existing on rotation
     * @param request    used to capture device hint and IP
     * @return the RAW token UUID — send to client, never store raw
     */
    String createRefreshToken(User user, String familyId, HttpServletRequest request);

    /**
     * Validates a submitted refresh token and revokes it in preparation for rotation.
     *
     * @throws com.ego.raw_ego.common.exception.AuthException if invalid, revoked (reuse), or expired
     */
    RefreshToken rotateRefreshToken(String rawToken);

    /**
     * Revokes a single refresh token by raw value. Silently ignores unknown tokens.
     */
    void revokeRefreshToken(String rawToken);

    /**
     * Revokes ALL active refresh tokens for a user.
     * Called on: password reset, account suspension, admin force-logout.
     */
    void revokeAllUserTokens(Long userId);

    /**
     * Computes SHA-256 of the raw token for storage and lookup.
     */
    String hashToken(String rawToken);
}
