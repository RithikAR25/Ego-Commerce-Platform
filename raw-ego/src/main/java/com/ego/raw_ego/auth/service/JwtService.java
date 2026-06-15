package com.ego.raw_ego.auth.service;

import com.ego.raw_ego.auth.entity.User;
import io.jsonwebtoken.Claims;

import java.time.Instant;

/**
 * Contract for JWT access token operations.
 *
 * <p>Token structure:
 * <pre>
 * Header : { "alg": "HS256" }
 * Payload: {
 *   "sub":    "user@email.com",
 *   "userId": 42,
 *   "role":   "CUSTOMER",
 *   "iat":    1716285600,
 *   "exp":    1716286500
 * }
 * </pre>
 */
public interface JwtService {

    /** Generates a signed JWT access token for the given user. */
    String generateAccessToken(User user);

    /**
     * Parses and validates the JWT, returning all claims.
     * Throws a JJWT exception if invalid.
     */
    Claims extractAllClaims(String token);

    /** Extracts the subject (email) from the token. */
    String extractEmail(String token);

    /**
     * Returns the issuedAt time as an {@link Instant} for the password_changed_at guard.
     */
    Instant extractIssuedAt(String token);

    /**
     * Returns true only if the token has a valid signature and has not expired.
     * Never throws — all exceptions are caught and logged.
     */
    boolean isTokenValid(String token);

    /**
     * Generates a 24-hour email verification JWT.
     *
     * @param email the user's email address
     * @return signed JWT string
     */
    String generateEmailVerificationToken(String email);

    /**
     * Validates an email verification token and extracts the user's email.
     *
     * @throws io.jsonwebtoken.JwtException if invalid, expired, or wrong type
     */
    String verifyEmailToken(String token);

    /**
     * Generates a 1-hour password reset JWT.
     *
     * @param email the user's email address
     * @return signed JWT string
     */
    String generatePasswordResetToken(String email);

    /**
     * Validates a password reset token and extracts the user's email.
     *
     * @throws io.jsonwebtoken.JwtException if invalid, expired, or wrong type
     */
    String verifyPasswordResetToken(String token);

    /** Returns the access token TTL in milliseconds. */
    long getAccessTokenExpiryMs();
}
