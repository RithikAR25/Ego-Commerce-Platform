package com.ego.raw_ego.auth.service;

import com.ego.raw_ego.auth.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

/**
 * JWT access token service using JJWT 0.12.3.
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
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Secret is stored as a 64-char hex string (= 256 bits) in application.properties.
 *       Decoded to bytes via {@link HexFormat} (Java 17+). Never store as plain text.</li>
 *   <li>Algorithm is HS256 (HMAC-SHA-256). Switch to RS256 when distributing
 *       token verification to external services (requires key-pair management).</li>
 *   <li>Access tokens are SHORT-LIVED (default 15 min). Refresh tokens handle longevity.</li>
 *   <li>{@code userId} and {@code role} are embedded as claims to avoid a DB hit on
 *       every authenticated request (traded against stale data risk — acceptable at 15 min).</li>
 * </ul>
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;

    public JwtService(
            @Value("${spring.jwt.secret}") String hexSecret,
            @Value("${spring.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs
    ) {
        // Parse the 64-char hex string into 32 raw bytes (256-bit key for HS256)
        this.secretKey = Keys.hmacShaKeyFor(HexFormat.of().parseHex(hexSecret));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    // ── Token generation ───────────────────────────────────────────────────────

    /**
     * Generates a signed JWT access token for the given user.
     * Embeds userId and role to avoid repeated DB lookups on each request.
     */
    public String generateAccessToken(User user) {
        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiryMs);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // ── Token parsing ──────────────────────────────────────────────────────────

    /**
     * Parses and validates the JWT, returning all claims.
     * Throws a JJWT exception if invalid — callers should use {@link #isTokenValid} first.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Returns the issuedAt time as an {@link Instant} for the password_changed_at guard.
     * The filter checks: if iat < user.passwordChangedAt → reject the token.
     */
    public Instant extractIssuedAt(String token) {
        Date issuedAt = extractAllClaims(token).getIssuedAt();
        return issuedAt != null ? issuedAt.toInstant() : Instant.EPOCH;
    }

    // ── Token validation ───────────────────────────────────────────────────────

    /**
     * Returns true only if the token has a valid signature and has not expired.
     * Never throws — all exceptions are caught and logged at the appropriate level.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature detected");
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token");
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token");
        } catch (IllegalArgumentException e) {
            log.warn("Empty or null JWT token");
        }
        return false;
    }

    // ── Email verification tokens ──────────────────────────────────────────────

    /**
     * Generates a short-lived JWT used as an email verification link token.
     *
     * <p>Token properties:
     * <ul>
     *   <li>Subject: user email (the unique identity)</li>
     *   <li>Claim "type": "EMAIL_VERIFY" — prevents access tokens being used as verify tokens</li>
     *   <li>TTL: 24 hours — long enough for the user to check their inbox</li>
     * </ul>
     *
     * <p>The token is embedded in the verification link:
     * {@code GET /api/v1/auth/verify-email?token=<jwt>}
     *
     * <p>Stateless design — no DB table required. The token is one-time-use in practice
     * because once {@code emailVerified=true} is set, subsequent calls are idempotent.
     *
     * @param email the user's email address
     * @return signed JWT string
     */
    public String generateEmailVerificationToken(String email) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(24 * 60 * 60); // 24 hours

        return Jwts.builder()
                .subject(email)
                .claim("type", "EMAIL_VERIFY")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validates an email verification token and extracts the user's email.
     *
     * @param token the JWT from the verification link
     * @return the email address embedded in the token
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or not of type EMAIL_VERIFY
     */
    public String verifyEmailToken(String token) {
        Claims claims = extractAllClaims(token); // throws JwtException on invalid/expired

        String type = claims.get("type", String.class);
        if (!"EMAIL_VERIFY".equals(type)) {
            throw new io.jsonwebtoken.MalformedJwtException(
                    "Token type mismatch: expected EMAIL_VERIFY but got " + type);
        }

        return claims.getSubject();
    }

    // ── Password reset tokens ──────────────────────────────────────────────────

    /**
     * Generates a short-lived JWT used as a password reset link token.
     *
     * <p>Token properties:
     * <ul>
     *   <li>Subject: user email (the unique identity)</li>
     *   <li>Claim "type": "PASSWORD_RESET" — prevents email-verify or access tokens being used here</li>
     *   <li>TTL: 1 hour — short to limit the attack window of a leaked reset link</li>
     * </ul>
     *
     * <p>The token is embedded in the reset link sent by email:
     * {@code GET /auth/reset-password?token=<jwt>} (handled by the frontend).
     * The user submits the token + new password to {@code POST /api/v1/auth/reset-password}.
     *
     * <p>Stateless design — no DB table required. One-time-use in practice because
     * resetting the password sets {@code passwordChangedAt=now()}, which invalidates
     * all existing JWTs (access and reset) issued before that timestamp.
     *
     * @param email the user's email address
     * @return signed JWT string
     */
    public String generatePasswordResetToken(String email) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(60 * 60); // 1 hour

        return Jwts.builder()
                .subject(email)
                .claim("type", "PASSWORD_RESET")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validates a password reset token and extracts the user's email.
     *
     * @param token the JWT from the reset link
     * @return the email address embedded in the token
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or not of type PASSWORD_RESET
     */
    public String verifyPasswordResetToken(String token) {
        Claims claims = extractAllClaims(token); // throws JwtException on invalid/expired

        String type = claims.get("type", String.class);
        if (!"PASSWORD_RESET".equals(type)) {
            throw new io.jsonwebtoken.MalformedJwtException(
                    "Token type mismatch: expected PASSWORD_RESET but got " + type);
        }

        return claims.getSubject();
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    /** Returns the access token TTL in milliseconds (used to compute expiresIn for the client). */
    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }
}
