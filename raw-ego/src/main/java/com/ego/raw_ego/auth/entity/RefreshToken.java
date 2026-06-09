package com.ego.raw_ego.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity mapping to the {@code refresh_tokens} table (schema_v2.sql).
 *
 * <p>Security design:
 * <ul>
 *   <li><b>token_hash</b>: SHA-256 of the raw UUID token. Raw tokens are NEVER stored.</li>
 *   <li><b>family_id</b>: Groups all rotated tokens from the same login session.
 *       On reuse of any revoked token in a family, the entire family is revoked
 *       (detects token theft).</li>
 *   <li><b>revoked_at</b>: NULL = valid. Non-null = revoked (logout / rotation / theft).</li>
 * </ul>
 *
 * <p>Lifecycle:
 * <pre>
 *   LOGIN        → new family_id (UUID), new token, revoked_at=NULL
 *   REFRESH      → current token revoked_at=NOW(), new token with SAME family_id
 *   LOGOUT       → current token revoked_at=NOW()
 *   REUSE DETECT → ALL tokens in family: revoked_at=NOW()
 * </pre>
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Eager is intentional here: every time we validate a refresh token,
     * we immediately need the user to check active/deleted status and
     * to generate the new access token. Lazy would cause N+1 in this flow.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex digest of the raw token UUID. Unique across all tokens. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    /**
     * UUID grouping all tokens from a single login session.
     * New on login; preserved on rotation; used for family-wide revocation.
     */
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    /** Truncated User-Agent string for display on "active sessions" screens. */
    @Column(name = "device_hint", length = 255)
    private String deviceHint;

    /** Client IP at the time of token creation (IPv4 or IPv6). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** NULL = token is still valid. Non-null = token was revoked. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Domain helpers ─────────────────────────────────────────────────────────

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** A token is valid only when it is neither revoked nor expired. */
    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }
}
