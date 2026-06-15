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
 * Implementation of {@link JwtService} — JWT access token service using JJWT 0.12.3.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Secret is stored as a 64-char hex string (= 256 bits) in application.properties.
 *       Decoded to bytes via {@link HexFormat} (Java 17+). Never store as plain text.</li>
 *   <li>Algorithm is HS256 (HMAC-SHA-256).</li>
 *   <li>Access tokens are SHORT-LIVED (default 15 min). Refresh tokens handle longevity.</li>
 *   <li>{@code userId} and {@code role} are embedded as claims to avoid a DB hit on
 *       every authenticated request.</li>
 * </ul>
 */
@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;

    public JwtServiceImpl(
            @Value("${spring.jwt.secret}") String hexSecret,
            @Value("${spring.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(HexFormat.of().parseHex(hexSecret));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    @Override
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

    @Override
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public Instant extractIssuedAt(String token) {
        Date issuedAt = extractAllClaims(token).getIssuedAt();
        return issuedAt != null ? issuedAt.toInstant() : Instant.EPOCH;
    }

    @Override
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

    @Override
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

    @Override
    public String verifyEmailToken(String token) {
        Claims claims = extractAllClaims(token);

        String type = claims.get("type", String.class);
        if (!"EMAIL_VERIFY".equals(type)) {
            throw new io.jsonwebtoken.MalformedJwtException(
                    "Token type mismatch: expected EMAIL_VERIFY but got " + type);
        }

        return claims.getSubject();
    }

    @Override
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

    @Override
    public String verifyPasswordResetToken(String token) {
        Claims claims = extractAllClaims(token);

        String type = claims.get("type", String.class);
        if (!"PASSWORD_RESET".equals(type)) {
            throw new io.jsonwebtoken.MalformedJwtException(
                    "Token type mismatch: expected PASSWORD_RESET but got " + type);
        }

        return claims.getSubject();
    }

    @Override
    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }
}
