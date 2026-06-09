package com.ego.raw_ego.auth.controller;

import com.ego.raw_ego.auth.dto.request.LoginRequest;
import com.ego.raw_ego.auth.dto.request.RefreshTokenRequest;
import com.ego.raw_ego.auth.dto.request.RegisterRequest;
import com.ego.raw_ego.auth.dto.response.AuthResponse;
import com.ego.raw_ego.auth.dto.response.UserResponse;
import com.ego.raw_ego.auth.service.AuthService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST controller.
 *
 * <p>All endpoints are under {@code /api/v1/auth} (public — no Bearer token required).
 * The /me and /resend-verification endpoints require authentication.
 *
 * <p>API Contract:
 * <pre>
 * POST /api/v1/auth/register          → 201 AuthResponse
 * POST /api/v1/auth/login             → 200 AuthResponse
 * POST /api/v1/auth/refresh           → 200 AuthResponse
 * POST /api/v1/auth/logout            → 200 void
 * GET  /api/v1/auth/me                → 200 UserResponse   [Bearer required]
 * POST /api/v1/auth/verify-email      → 200 void
 * POST /api/v1/auth/resend-verification → 200 void         [Bearer required]
 * POST /api/v1/auth/forgot-password   → 200 void           [public, no enumeration]
 * POST /api/v1/auth/reset-password    → 200 void           [public, JWT token body]
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register, login, token lifecycle, and profile endpoints")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── Register ───────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(
            summary     = "Register a new customer account",
            description = "Creates a new CUSTOMER account, hashes the password with BCrypt-12, " +
                          "and returns an access + refresh token pair. " +
                          "Email verification is sent asynchronously (Phase 7)."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthResponse response = authService.register(request, httpRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Account created successfully. Please verify your email to unlock all features.",
                        response
                ));
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(
            summary     = "Login with email and password",
            description = "Authenticates via BCrypt comparison. Returns a token pair on success. " +
                          "Returns the same error for wrong email OR wrong password (prevents enumeration)."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(
            summary     = "Rotate refresh token and issue a new access token",
            description = "Validates the submitted refresh token, revokes it (rotation), " +
                          "and returns a new access + refresh token pair with the same family_id. " +
                          "Submitting an already-revoked token revokes the entire session family " +
                          "(theft detection)."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthResponse response = authService.refreshToken(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully.", response));
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary     = "Logout — revoke refresh token and immediately blocklist access token",
            description = "Revokes the refresh token in the database AND adds the current access token " +
                          "to a Redis blocklist (TTL = remaining token validity). " +
                          "The access token is immediately invalid — no waiting for natural expiry. " +
                          "To log out from ALL devices, call the dedicated /logout-all endpoint (Phase 2)."
    )
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        // Extract the raw access token from the Authorization header for blocklisting
        String authHeader    = httpRequest.getHeader("Authorization");
        String rawAccessToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : null;

        authService.logout(request.getRefreshToken(), rawAccessToken);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully."));
    }

    // ── Email verification ─────────────────────────────────────────────────────

    @PostMapping("/verify-email")
    @Operation(
            summary     = "Verify email address using token from verification email",
            description = "Validates the signed JWT token embedded in the verification link. " +
                          "Marks emailVerified=true on the user account. " +
                          "Idempotent — safe to call multiple times with the same or a new token. " +
                          "Token TTL: 24 hours."
    )
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam("token") String token
    ) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully. You can now place orders."));
    }

    @PostMapping("/resend-verification")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary     = "Re-send email verification link",
            description = "Generates a new 24-hour verification JWT and sends it to the authenticated user's " +
                          "email address. No-op if the account is already verified."
    )
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        authService.resendVerificationEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                "Verification email sent. Please check your inbox."));
    }

    // ── Password Reset ─────────────────────────────────────────────────────────

    /**
     * Initiates the password reset flow.
     *
     * <p>Always returns 200 OK regardless of whether the email is registered —
     * this prevents user enumeration attacks.
     */
    @PostMapping("/forgot-password")
    @Operation(
            summary     = "Request a password reset email",
            description = "Generates a 1-hour signed JWT reset link and emails it to the address if registered. " +
                          "Always returns 200 — no indication of whether the email exists (prevents enumeration)."
    )
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                "If that email is registered, a password reset link has been sent."));
    }

    /**
     * Completes the password reset flow using the JWT token from the reset email.
     *
     * <p>Validates the token type (must be PASSWORD_RESET), enforces password complexity,
     * hashes the new password with BCrypt-12, and sets passwordChangedAt=now() to
     * immediately invalidate all existing sessions.
     */
    @PostMapping("/reset-password")
    @Operation(
            summary     = "Reset password using token from reset email",
            description = "Validates the PASSWORD_RESET JWT and updates the user's password. " +
                          "Sets passwordChangedAt=now() which immediately invalidates all existing JWTs. " +
                          "Token TTL: 1 hour."
    )
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(
                "Password updated successfully. Please log in with your new password."));
    }

    // ── Get Current User ───────────────────────────────────────────────────────

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary     = "Get the currently authenticated user's profile",
            description = "Extracts the user email from the validated JWT and returns the public profile. " +
                          "Sensitive fields (passwordHash, version, deletedAt) are excluded from the response."
    )
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserResponse user = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ── Request records (lightweight DTOs) ────────────────────────────────────

    /** Body for POST /forgot-password — just the email address. */
    record ForgotPasswordRequest(String email) {}

    /** Body for POST /reset-password — the JWT token from the email and the new password. */
    record ResetPasswordRequest(String token, String newPassword) {}
}
