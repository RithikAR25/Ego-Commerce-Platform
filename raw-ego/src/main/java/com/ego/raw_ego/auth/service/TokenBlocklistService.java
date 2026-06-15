package com.ego.raw_ego.auth.service;

/**
 * Contract for the Redis-backed access token blocklist.
 *
 * <p>Implements immediate AT invalidation on logout. Without this, access tokens
 * remain valid until their natural expiry (~15 min) even after logout.
 */
public interface TokenBlocklistService {

    /**
     * Adds the given access token to the Redis blocklist with TTL = remaining validity.
     * Fails silently on Redis errors to prevent logout from breaking.
     *
     * @param rawAccessToken the raw JWT string from the Authorization header
     */
    void block(String rawAccessToken);

    /**
     * Returns {@code true} if the given access token is present in the blocklist.
     * Fails safe to {@code false} on Redis errors.
     *
     * @param rawAccessToken the raw JWT string
     */
    boolean isBlocked(String rawAccessToken);
}
