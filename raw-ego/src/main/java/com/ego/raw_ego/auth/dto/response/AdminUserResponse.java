package com.ego.raw_ego.auth.dto.response;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Admin-safe user DTO for the {@code GET /api/v1/admin/users} endpoints.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Deliberately narrower than {@link UserResponse} — exposes only fields
 *       needed by the admin user management UI.</li>
 *   <li><strong>Never</strong> exposes: {@code passwordHash}, {@code passwordChangedAt},
 *       {@code version}, {@code deleted}, {@code emailVerified}, {@code lastLoginAt}.</li>
 *   <li>Uses the same static {@code from(User)} factory pattern as {@link UserResponse}
 *       so the mapping stays co-located with the DTO class.</li>
 * </ul>
 *
 * <p>Supports future expansion: role changes and account suspension can be added
 * as separate PATCH endpoints without modifying this read DTO.
 */
@Getter
@Builder
public class AdminUserResponse {

    private final Long     id;
    private final String   firstName;
    private final String   lastName;
    private final String   email;
    private final UserRole role;

    /**
     * Whether the account is active (true) or suspended (false).
     * Maps to {@code is_active} in the {@code users} table.
     */
    private final boolean active;

    /** Timestamp when the user registered. Sourced from {@code created_at}. */
    private final Instant createdAt;

    /**
     * Maps a {@link User} entity to this admin DTO.
     * Called from {@link com.ego.raw_ego.auth.service.AdminUserService} — never in a loop
     * that would trigger per-row queries.
     *
     * @param user a non-null, non-deleted User entity
     * @return safe admin DTO with no sensitive fields
     */
    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
