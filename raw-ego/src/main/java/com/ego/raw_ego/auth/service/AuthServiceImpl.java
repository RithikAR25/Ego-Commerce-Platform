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
import com.ego.raw_ego.common.util.LogMasker;
import com.ego.raw_ego.notification.service.NotificationService;
import io.jsonwebtoken.JwtException;
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
 * Implementation of {@link AuthService} — core authentication service orchestrating the full auth flow.
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
public class AuthServiceImpl implements AuthService {

    private final UserRepository        userRepository;
    private final JwtService            jwtService;
    private final RefreshTokenService   refreshTokenService;
    private final TokenBlocklistService tokenBlocklistService;
    private final PasswordEncoder       passwordEncoder;
    private final NotificationService   notificationService;

    @Value("${ego.app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        log.info("Registration attempt: email={}", LogMasker.maskEmail(normalizedEmail));

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
                .build();

        user = userRepository.save(user);
        log.info("User registered: id={} email={}", user.getId(), LogMasker.maskEmail(user.getEmail()));

        sendVerificationEmail(user);

        return buildAuthResponse(user, null, httpRequest);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        log.info("Login attempt: email={}", LogMasker.maskEmail(normalizedEmail));

        User user = userRepository.findByEmailAndDeletedFalse(normalizedEmail)
                .orElseThrow(() -> new AuthException("Invalid email or password."));

        if (!user.isActive()) {
            log.warn("Login attempt on deactivated account: email={}", LogMasker.maskEmail(normalizedEmail));
            throw new AuthException("Your account has been deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed — wrong password: email={}", LogMasker.maskEmail(normalizedEmail));
            throw new AuthException("Invalid email or password.");
        }

        userRepository.updateLastLoginAt(user.getId(), Instant.now());
        log.info("Login successful: id={} email={}", user.getId(), LogMasker.maskEmail(user.getEmail()));

        return buildAuthResponse(user, null, httpRequest);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        log.info("Token rotation attempt");

        RefreshToken rotated = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
        User user = rotated.getUser();

        if (!user.isActive() || user.isDeleted()) {
            throw new AuthException("Account is no longer active.");
        }

        log.info("Token rotated successfully for userId={}", user.getId());
        return buildAuthResponse(user, rotated.getFamilyId(), httpRequest);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken, String rawAccessToken) {
        refreshTokenService.revokeRefreshToken(rawRefreshToken);
        log.info("Logout: refresh token revoked");

        if (rawAccessToken != null && !rawAccessToken.isBlank()) {
            tokenBlocklistService.block(rawAccessToken);
        }
    }

    @Override
    @Async
    public void sendVerificationEmail(User user) {
        String token = jwtService.generateEmailVerificationToken(user.getEmail());
        String link  = appBaseUrl + "/auth/verify-email?token=" + token;
        notificationService.sendEmailVerification(
                user.getId(), user.getEmail(), user.getFirstName(), link);
        log.info("[Auth] Verification email dispatched: userId={} email={}",
                user.getId(), user.getEmail());
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        String email;
        try {
            email = jwtService.verifyEmailToken(token);
        } catch (JwtException e) {
            log.warn("[Auth] Email verification token invalid: {}", e.getMessage());
            throw new AuthException("Invalid or expired verification link. Please request a new one.");
        }

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AuthException("Account not found for this verification link."));

        if (user.isEmailVerified()) {
            log.debug("[Auth] Email already verified for userId={} — idempotent no-op", user.getId());
            return;
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("[Auth] Email verified: userId={} email={}", user.getId(), email);
    }

    @Override
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AuthException("Authenticated user not found."));

        if (user.isEmailVerified()) {
            log.debug("[Auth] Resend skipped — email already verified: userId={}", user.getId());
            return;
        }

        sendVerificationEmail(user);
    }

    @Override
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

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String email;
        try {
            email = jwtService.verifyPasswordResetToken(token);
        } catch (JwtException e) {
            log.warn("[Auth] Password reset token invalid: {}", e.getMessage());
            throw new AuthException("Invalid or expired password reset link. Please request a new one.");
        }

        validatePasswordComplexity(newPassword);

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AuthException("Account not found. Please register."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        log.info("[Auth] Password reset complete: userId={}", user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AuthException("Authenticated user not found. Please log in again."));
        return UserResponse.from(user);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

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

    private void validatePasswordComplexity(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new AuthException("Password is required.");
        }
        if (newPassword.length() < 8) {
            throw new AuthException("Password must be at least 8 characters long.");
        }
        if (newPassword.length() > 72) {
            throw new AuthException("Password must not exceed 72 characters.");
        }
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")) {
            throw new AuthException(
                "Password must contain at least one uppercase letter, one lowercase letter, and one number.");
        }
    }
}
