package com.ego.raw_ego.auth.service;

import com.ego.raw_ego.auth.dto.request.LoginRequest;
import com.ego.raw_ego.auth.dto.request.RefreshTokenRequest;
import com.ego.raw_ego.auth.dto.request.RegisterRequest;
import com.ego.raw_ego.auth.dto.response.AuthResponse;
import com.ego.raw_ego.auth.dto.response.UserResponse;
import com.ego.raw_ego.auth.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Contract for the core authentication service — orchestrates the full auth flow.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Does NOT inject AuthenticationManager — direct password verification
 *       via PasswordEncoder avoids circular dependency risks.</li>
 *   <li>Error messages for login always say "Invalid email or password" regardless
 *       of which field is wrong — prevents user enumeration.</li>
 * </ul>
 */
public interface AuthService {

    /**
     * Registers a new customer account and returns an immediate token pair.
     */
    AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest);

    /**
     * Authenticates a user with email + password and returns a token pair.
     */
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * Rotates a refresh token and issues a new access + refresh token pair.
     *
     * @throws com.ego.raw_ego.common.exception.AuthException if token is invalid, expired, revoked, or reuse detected
     */
    AuthResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest);

    /**
     * Logs out the user by revoking the refresh token and blocklisting the access token.
     *
     * @param rawRefreshToken the refresh token from the request body
     * @param rawAccessToken  the access token from the Authorization header (may be null)
     */
    void logout(String rawRefreshToken, String rawAccessToken);

    /**
     * Sends a verification email asynchronously. Runs on the ego-async-* thread pool.
     */
    void sendVerificationEmail(User user);

    /**
     * Verifies a user's email address using the signed JWT token from the link.
     *
     * @throws com.ego.raw_ego.common.exception.AuthException if the token is invalid or expired
     */
    void verifyEmail(String token);

    /**
     * Re-dispatches the verification email. Silent no-op if already verified.
     */
    void resendVerificationEmail(String email);

    /**
     * Initiates the password reset flow. Always returns successfully (no enumeration).
     */
    void forgotPassword(String email);

    /**
     * Completes the password reset flow — validates the JWT token, then updates the password.
     *
     * @throws com.ego.raw_ego.common.exception.AuthException if the token is invalid or expired
     */
    void resetPassword(String token, String newPassword);

    /**
     * Returns the public profile of the currently authenticated user.
     */
    UserResponse getCurrentUser(String email);
}
