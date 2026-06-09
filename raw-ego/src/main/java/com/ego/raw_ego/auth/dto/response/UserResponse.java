package com.ego.raw_ego.auth.dto.response;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Public user profile — safe to return to clients.
 *
 * <p>Deliberately omits: passwordHash, version, deleted, passwordChangedAt.
 * Uses a static factory so the mapping stays co-located with the response class,
 * not scattered across service methods.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private final Long id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final UserRole role;
    private final boolean emailVerified;
    private final boolean active;
    private final Instant createdAt;
    private final Instant lastLoginAt;

    /**
     * Maps a {@link User} entity to a safe public DTO.
     * All sensitive fields are excluded by design.
     */
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
