package com.ego.raw_ego.auth.entity;

import com.ego.raw_ego.auth.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;

/**
 * JPA entity mapping to the {@code users} table (defined in schema_v2.sql).
 *
 * <p>Implements {@link UserDetails} so this entity can be used directly as
 * the Spring Security principal — no separate adapter class needed.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Boolean fields are named WITHOUT the "is" prefix to avoid Lombok
 *       generating broken "isIsXxx()" getters. @Column maps the correct DB name.</li>
 *   <li>{@code @Builder.Default} ensures builder calls get the correct defaults
 *       (role=CUSTOMER, active=true) without needing explicit set calls.</li>
 *   <li>{@code passwordChangedAt} is checked in {@link com.ego.raw_ego.auth.security.JwtAuthenticationFilter}
 *       to invalidate JWTs issued before a password reset.</li>
 *   <li>{@code @Version} enables optimistic locking (matches schema's version column).</li>
 * </ul>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /**
     * BCrypt hash (strength 12). Never the raw password.
     * Column name: password_hash — "password" is a reserved word in some SQL dialects.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Role stored as the lowercase DB ENUM value ("customer" / "admin").
     * Spring Security converts via UserRole.toGrantedAuthority().
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('CUSTOMER','ADMIN') DEFAULT 'CUSTOMER'")
    @Builder.Default
    private UserRole role = UserRole.CUSTOMER;

    /** Maps to: is_active TINYINT(1). False = account suspended. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Maps to: is_email_verified TINYINT(1). */
    @Column(name = "is_email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /** Maps to: is_deleted TINYINT(1). Soft-delete — never hard-delete users. */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * When set, all JWTs with iat < this timestamp are considered invalid.
     * Must be updated on every successful password change/reset.
     */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Optimistic lock — matches schema's version INT UNSIGNED NOT NULL DEFAULT 1. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── UserDetails contract ───────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role.toGrantedAuthorities();
    }

    /** Spring Security calls this to get the credential for comparison. */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Spring Security identifies users by email (the unique login key). */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // We use is_active + is_deleted instead of expiry
    }

    @Override
    public boolean isAccountNonLocked() {
        return active && !deleted;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Credential expiry is handled via passwordChangedAt + JWT iat check
    }

    @Override
    public boolean isEnabled() {
        return active && !deleted;
    }
}
