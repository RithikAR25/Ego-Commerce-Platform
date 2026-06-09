package com.ego.raw_ego.auth.service;

import com.ego.raw_ego.auth.dto.request.LoginRequest;
import com.ego.raw_ego.auth.dto.request.RefreshTokenRequest;
import com.ego.raw_ego.auth.dto.request.RegisterRequest;
import com.ego.raw_ego.auth.dto.response.AuthResponse;
import com.ego.raw_ego.auth.dto.response.UserResponse;
import com.ego.raw_ego.auth.entity.RefreshToken;
import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.enums.UserRole;
import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.common.exception.AuthException;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Core authentication service — orchestrates the full auth flow.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Does NOT inject {@code AuthenticationManager} — direct password verification
 *       via {@link PasswordEncoder#matches} avoids circular dependency risks and
 *       reduces the DB query count on login from 2 to 1.</li>
 *   <li>All public methods are {@code @Transactional} to ensure atomicity.
 *       If token creation fails after user creation, the registration rolls back.</li>
 *   <li>Error messages for login always say "Invalid email or password" regardless
 *       of whether the email or password is wrong — prevents user enumeration.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepository;
    private final JwtService            jwtService;
    private final RefreshTokenService   refreshTokenService;
    private final TokenBlocklistService tokenBlocklistService;
    private final PasswordEncoder       passwordEncoder;
    private final NotificationService   notificationService;

    /** Frontend base URL for building verification links. */
    @Value("${ego.app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    // ── Register ───────────────────────────────────────────────────────────────

    /**
     * Registers a new customer account and returns an immediate token pair.
     *
     * <p>Flow:
     * <ol>
     *   <li>Check email uniqueness (409 on duplicate)</li>
     *   <li>BCrypt-hash the password</li>
     *   <li>Persist User (role=CUSTOMER, active=true, emailVerified=false)</li>
     *   <li>Generate access + refresh tokens</li>
     * </ol>
     *
     * <p>Email verification is NOT enforced at login — users may use the site
     * immediately. Checkout will enforce verification at order placement time (Phase 6).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        log.info("Registration attempt: email={}", normalizedEmail);

        if (userRepository.existsByEmailAndDeletedFalse(normalizedEmail)) {
            throw new ConflictException("An account with this email address already exists.");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone())
                .role(UserRole.CUSTOMER)
                .build(); // Builder.Default handles: active=true, emailVerified=false, deleted=false, version=1

        user = userRepository.save(user);
        log.info("User registered: id={} email={}", user.getId(), user.getEmail());

        // Send verification email asynchronously — never blocks the registration response.
        // The @Async annotation on sendVerificationEmail() runs this on ego-async-* thread pool.
        sendVerificationEmail(user);

        return buildAuthResponse(user, null, httpRequest);
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user with email + password and returns a token pair.
     *
     * <p>Flow:
     * <ol>
     *   <li>Load user by email (not found = same error as wrong password)</li>
     *   <li>Check account is active and not deleted</li>
     *   <li>Verify BCrypt password</li>
     *   <li>Update last_login_at (non-blocking — uses targeted JPQL update)</li>
     *   <li>Generate access + refresh tokens with a new family_id</li>
     * </ol>
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        log.info("Login attempt: email={}", normalizedEmail);

        User user = userRepository.findByEmailAndDeletedFalse(normalizedEmail)
                .orElseThrow(() -> new AuthException("Invalid email or password."));

        if (!user.isActive()) {
            log.warn("Login attempt on deactivated account: email={}", normalizedEmail);
            throw new AuthException("Your account has been deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed — wrong password: email={}", normalizedEmail);
            throw new AuthException("Invalid email or password.");
        }

        // Non-version-bumping targeted update — avoids optimistic lock conflict
        userRepository.updateLastLoginAt(user.getId(), Instant.now());
        log.info("Login successful: id={} email={}", user.getId(), user.getEmail());

        return buildAuthResponse(user, null, httpRequest); // null = new family_id on each login
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    /**
     * Rotates a refresh token and issues a new access + refresh token pair.
     *
     * <p>Flow:
     * <ol>
     *   <li>Delegate rotation to {@link RefreshTokenService#rotateRefreshToken}</li>
     *   <li>Check user is still active</li>
     *   <li>Generate new access token</li>
     *   <li>Create new refresh token with the SAME family_id (rotation within family)</li>
     * </ol>
     *
     * @throws AuthException if token is invalid, expired, revoked, or reuse is detected
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        log.debug("Token rotation attempt");

        RefreshToken rotated = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
        User user = rotated.getUser();

        if (!user.isActive() || user.isDeleted()) {
            throw new AuthException("Account is no longer active.");
        }

        log.debug("Token rotated for user: email={}", user.getEmail());
        return buildAuthResponse(user, rotated.getFamilyId(), httpRequest);
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    /**
     * Logs out the user by:
     * <ol>
     *   <li>Revoking the refresh token in the database (prevents future silent refresh).</li>
     *   <li>Adding the access token to the Redis blocklist with TTL = remaining expiry
     *       (prevents use of the AT for the remainder of its natural validity window).</li>
     * </ol>
     *
     * <p>The two operations are decoupled — a Redis failure will not prevent the RT revocation
     * from completing. This ensures logout always succeeds at the database level.
     *
     * @param rawRefreshToken the refresh token from the request body
     * @param rawAccessToken  the access token extracted from the Authorization header
     *                        (may be null if the client sent no header)
     */
    @Transactional
    public void logout(String rawRefreshToken, String rawAccessToken) {
        refreshTokenService.revokeRefreshToken(rawRefreshToken);
        log.debug("Logout: refresh token revoked");

        // Blocklist the access token to prevent it being used after logout.
        // Non-fatal if rawAccessToken is null (e.g. client sent no Bearer header).
        if (rawAccessToken != null && !rawAccessToken.isBlank()) {
            tokenBlocklistService.block(rawAccessToken);
        }
    }

    // ── Email verification ───────────────────────────────────────────────────────────

    /**
     * Sends a verification email containing a signed JWT link (24h TTL) to the
     * given user. Called automatically on registration and on explicit re-send request.
     *
     * <p>Runs on the {@code ego-async-*} thread pool so it never blocks the
     * registration HTTP response.
     *
     * @param user the user whose email should be verified
     */
    @Async
    public void sendVerificationEmail(User user) {
        String token = jwtService.generateEmailVerificationToken(user.getEmail());
        // Link format: {frontend}/auth/verify-email?token=<jwt>
        // The frontend page calls POST /api/v1/auth/verify-email with the token.
        String link  = appBaseUrl + "/auth/verify-email?token=" + token;
        notificationService.sendEmailVerification(
                user.getId(), user.getEmail(), user.getFirstName(), link);
        log.info("[Auth] Verification email dispatched: userId={} email={}",
                user.getId(), user.getEmail());
    }

    /**
     * Verifies a user's email address using the signed JWT token from the link.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate the JWT (signature + expiry + type=EMAIL_VERIFY)</li>
     *   <li>Load user by the email embedded in the token subject</li>
     *   <li>Set {@code emailVerified=true} (idempotent — safe to call multiple times)</li>
     * </ol>
     *
     * @param token the raw JWT from the verification link
     * @throws AuthException if the token is invalid, expired, or the email is not found
     */
    @Transactional
    public void verifyEmail(String token) {
        String email;
        try {
            email = jwtService.verifyEmailToken(token);
        } catch (Exception e) {
            log.warn("[Auth] Email verification token invalid: {}", e.getMessage());
            throw new AuthException("Invalid or expired verification link. Please request a new one.");
        }

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AuthException("Account not found for this verification link."));

        if (user.isEmailVerified()) {
            log.debug("[Auth] Email already verified for userId={} — idempotent no-op", user.getId());
            return; // Already verified — idempotent
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("[Auth] Email verified: userId={} email={}", user.getId(), email);
    }

    /**
     * Loads the authenticated user from DB and re-dispatches the verification email.
     * Called by the {@code POST /api/v1/auth/resend-verification} endpoint.
     *
     * <p>Silently no-ops if the account is already verified — the email is not re-sent.
     * This prevents the endpoint being used to spam the user's inbox.
     *
     * @param email the authenticated user's email (from JWT principal)
     */
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AuthException("Authenticated user not found."));

        if (user.isEmailVerified()) {
            log.debug("[Auth] Resend skipped — email already verified: userId={}", user.getId());
            return;
        }

        sendVerificationEmail(user); // @Async — non-blocking
    }

    /**
     * Initiates the password reset flow for the given email address.
     *
     * <p>Flow:
     * <ol>
     *   <li>Load user by email — if not found, silently return (no enumeration leak)</li>
     *   <li>Generate a 1-hour {@code PASSWORD_RESET} JWT via {@link JwtService}</li>
     *   <li>Dispatch a password reset email with the link (non-blocking via @Async in NotificationService)</li>
     * </ol>
     *
     * <p><b>Important:</b> This method ALWAYS returns successfully regardless of whether
     * the email is registered. The caller must not expose this distinction to the client.
     *
     * @param email the email address submitted by the user
     */
    public void forgotPassword(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        log.info("[Auth] Password reset requested: email={}", normalizedEmail);

        userRepository.findByEmailAndDeletedFalse(normalizedEmail).ifPresent(user -> {
            String token = jwtService.generatePasswordResetToken(normalizedEmail);
            String link  = appBaseUrl + "/auth/reset-password?token=" + token;
            notificationService.sendPasswordResetEmail(
                    user.getId(), normalizedEmail, user.getFirstName(), link);
            log.info("[Auth] Password reset email dispatched: userId={}", user.getId());
        });
    }

    /**
     * Completes the password reset flow — validates the JWT token, then updates the password.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate the JWT (signature + expiry + type=PASSWORD_RESET)</li>
     *   <li>Load the user by the email embedded in the token</li>
     *   <li>Validate new password: min 8 chars, at least one digit or symbol</li>
     *   <li>BCrypt-12 hash + persist. Set {@code passwordChangedAt=now()} to invalidate existing sessions.</li>
     * </ol>
     *
     * @param token       the raw JWT from the reset link
     * @param newPassword the new password chosen by the user
     * @throws AuthException if the token is invalid, expired, or the account is not found
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String email;
        try {
            email = jwtService.verifyPasswordResetToken(token);
        } catch (Exception e) {
            log.warn("[Auth] Password reset token invalid: {}", e.getMessage());
            throw new AuthException("Invalid or expired password reset link. Please request a new one.");
        }

        // Basic password validation
        if (newPassword == null || newPassword.length() < 8) {
            throw new AuthException("Password must be at least 8 characters long.");
        }
        if (!newPassword.matches(".*[0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new AuthException("Password must contain at least one digit or special character.");
        }

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AuthException("Account not found. Please register."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now()); // Invalidates all JWTs issued before this moment
        userRepository.save(user);

        log.info("[Auth] Password reset complete: userId={}", user.getId());
    }

    // ── Get Current User ───────────────────────────────────────────────────────

    /**
     * Returns the public profile of the currently authenticated user.
     * The email is extracted from the validated JWT by the filter.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AuthException("Authenticated user not found. Please log in again."));
        return UserResponse.from(user);
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user, String familyId, HttpServletRequest httpRequest) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user, familyId, httpRequest);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiryMs() / 1000,
                UserResponse.from(user)
        );
    }
}
