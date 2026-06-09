package com.ego.raw_ego.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for:
 * <ul>
 *   <li>{@code POST /api/v1/auth/refresh} — rotate tokens</li>
 *   <li>{@code POST /api/v1/auth/logout}  — revoke token</li>
 * </ul>
 *
 * The client sends the raw refresh token UUID received at login/refresh time.
 * The service hashes it (SHA-256) before any DB lookup.
 */
@Getter
@Setter
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
