package com.ego.raw_ego.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Authentication response returned on register, login, and token refresh.
 *
 * <pre>
 * {
 *   "accessToken":  "eyJhbGci...",
 *   "refreshToken": "550e8400-e29b-...",   ← raw UUID; client must store securely
 *   "tokenType":    "Bearer",
 *   "expiresIn":    900,                   ← access token TTL in seconds
 *   "user": { ... }
 * }
 * </pre>
 *
 * <p>Client storage guidance:
 * <ul>
 *   <li>accessToken  → in-memory (never localStorage — XSS risk)</li>
 *   <li>refreshToken → httpOnly cookie or secure storage</li>
 * </ul>
 */
@Getter
@Builder
public class AuthResponse {

    private final String accessToken;

    /** Raw UUID token — client must hash before any comparison. Service stores SHA-256 hash. */
    private final String refreshToken;

    private final String tokenType;

    /** Access token TTL in seconds (default: 900 = 15 minutes). */
    private final long expiresIn;

    private final UserResponse user;

    public static AuthResponse of(String accessToken,
                                  String refreshToken,
                                  long expiresInSeconds,
                                  UserResponse user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresInSeconds)
                .user(user)
                .build();
    }
}
