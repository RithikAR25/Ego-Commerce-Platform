package com.ego.raw_ego.auth.repository;

import com.ego.raw_ego.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for the {@code refresh_tokens} table.
 *
 * <p>Key operations:
 * <ul>
 *   <li>Lookup by token_hash (used during refresh and logout)</li>
 *   <li>Family-wide revocation (used on reuse detection)</li>
 *   <li>User-wide revocation (used on logout-all-devices, password reset)</li>
 * </ul>
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Looks up a token by its SHA-256 hash. Raw tokens are never stored.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes all active tokens belonging to a token family.
     * Called immediately on reuse detection (theft scenario).
     *
     * @return number of rows updated (useful for audit logging)
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revokedAt = :revokedAt
             WHERE rt.familyId  = :familyId
               AND rt.revokedAt IS NULL
            """)
    int revokeAllByFamilyId(@Param("familyId") String familyId,
                            @Param("revokedAt") Instant revokedAt);

    /**
     * Revokes all active tokens for a specific user.
     * Called on: password reset, account suspension, logout-from-all-devices.
     *
     * @return number of rows updated
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revokedAt = :revokedAt
             WHERE rt.user.id   = :userId
               AND rt.revokedAt IS NULL
            """)
    int revokeAllByUserId(@Param("userId") Long userId,
                          @Param("revokedAt") Instant revokedAt);

    /**
     * Counts the number of currently active (non-revoked, non-expired) refresh token
     * families for a user — each represents one logged-in device/session.
     *
     * <p>A family is "active" when at least one token in it is still valid.
     * We simplify this by counting distinct family IDs where revokedAt IS NULL
     * and expiresAt is in the future.
     *
     * @param userId    the user's primary key
     * @param threshold Instant.now() — tokens expiring after this are considered active
     * @return number of active sessions
     */
    @Query("""
            SELECT COUNT(DISTINCT rt.familyId)
              FROM RefreshToken rt
             WHERE rt.user.id    = :userId
               AND rt.revokedAt  IS NULL
               AND rt.expiresAt  > :threshold
            """)
    long countActiveSessionsByUserId(@Param("userId") Long userId,
                                     @Param("threshold") Instant threshold);

    /**
     * Finds the oldest active token families for a user, ordered by creation time ascending.
     * Used to evict the oldest session(s) when the per-user session cap is exceeded.
     *
     * @param userId    the user's primary key
     * @param threshold Instant.now()
     * @return list of active tokens ordered oldest-first
     */
    @Query("""
            SELECT rt FROM RefreshToken rt
             WHERE rt.user.id   = :userId
               AND rt.revokedAt IS NULL
               AND rt.expiresAt > :threshold
             ORDER BY rt.createdAt ASC
            """)
    List<RefreshToken> findActiveSessionsOldestFirst(@Param("userId") Long userId,
                                                     @Param("threshold") Instant threshold);
}
