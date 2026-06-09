# JWT Flow — EGO Platform

> **Library:** JJWT 0.12.3
> **Algorithm:** HS256 (HMAC-SHA-256)
> **Secret:** 256-bit key stored as hex in application.properties

---

## Token Anatomy

```
Header.Payload.Signature

Header:  { "alg": "HS256" }
Payload: {
  "sub":    "user@ego.com",     ← email = Spring Security username
  "userId": 42,                 ← embedded to avoid DB lookup per request
  "role":   "CUSTOMER",         ← embedded for @PreAuthorize checks
  "iat":    1716285600,         ← issued-at (Unix epoch seconds)
  "exp":    1716286500          ← expiry (iat + 900s = 15 min)
}
```

---

## Token Lifetimes

| Token | TTL | Storage Recommendation |
|-------|-----|------------------------|
| Access Token | 15 minutes (900s) | In-memory only — never localStorage (XSS risk) |
| Refresh Token | 30 days | httpOnly Secure cookie or secure device storage |

---

## Generation Flow

```
JwtService.generateAccessToken(user)
    │
    ├── Instant now    = Instant.now()
    ├── Instant expiry = now + 900_000ms
    │
    └── Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId())
            .claim("role",   user.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
            → "eyJhbGci..."
```

---

## Validation Flow (Per Request)

```
Request arrives with: Authorization: Bearer eyJhbGci...

JwtAuthenticationFilter.doFilterInternal()
    │
    ├── 1. Extract header — no header? → pass through (public endpoints OK)
    │
    ├── 2. JwtService.isTokenValid(token)
    │       └── Jwts.parser().verifyWith(secretKey).build()
    │                .parseSignedClaims(token)
    │           ├── SignatureException    → false (log WARN)
    │           ├── ExpiredJwtException  → false (log DEBUG)
    │           ├── MalformedJwtException → false (log WARN)
    │           └── success              → true
    │
    ├── 3. Extract claims → get subject (email) + issuedAt
    │
    ├── 4. UserDetailsService.loadUserByUsername(email) → User entity
    │
    ├── 5. passwordChangedAt guard:
    │       IF user.passwordChangedAt != null
    │         AND token.iat BEFORE user.passwordChangedAt
    │       THEN → reject (continue chain without setting context)
    │       RATIONALE: After password reset, old tokens are instantly invalid
    │                  without needing a blocklist or token revocation table.
    │
    ├── 6. Check user.isEnabled() && user.isAccountNonLocked()
    │
    └── 7. Set SecurityContextHolder:
            UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
            )
```

---

## Secret Key Management

```
application.properties (development):
  spring.jwt.secret=<your-256-bit-hex-secret>

Parsing in JwtService constructor:
  Keys.hmacShaKeyFor(HexFormat.of().parseHex(hexSecret))
  → 64 hex chars = 32 bytes = 256 bits → sufficient for HS256

Production:
  Override via environment variable:
  JWT_SECRET=<your-256-bit-hex-secret>
  spring.jwt.secret=${JWT_SECRET}

Key rotation:
  1. Generate new 256-bit hex key
  2. Set new key in env
  3. Rolling restart (old tokens expire within 15 min)
  4. No DB migration required for key rotation
```

---

## passwordChangedAt Guard — Instant AT Invalidation

```
Scenario: User changes password → all prior JWTs must be rejected immediately.

Without this guard:
  Old access token (issued before password change) would be valid for up to 15 min.
  With a Redis blocklist, you could revoke immediately — but Redis is Phase 2.

With this guard (zero infrastructure required):
  1. On password change:
     UPDATE users SET password_changed_at = NOW() WHERE id = ?

  2. On each request through JwtAuthenticationFilter:
     IF token.iat < user.password_changed_at → reject

  Cost: 1 DB read per authenticated request (unavoidable without caching).
  Future: Cache user.password_changed_at in Redis with TTL = access token expiry.
```

---

## Sequence Diagram — Full Login + Authenticated Request

```
Client                    AuthController        AuthService          JwtService          DB
  │                            │                    │                    │               │
  │── POST /auth/login ────────►│                    │                    │               │
  │   {email, password}        │── login() ─────────►│                    │               │
  │                            │                    │── findByEmail ──────────────────────►│
  │                            │                    │◄── User entity ─────────────────────│
  │                            │                    │── BCrypt.matches() │               │
  │                            │                    │── generateAccessToken ──────────────►│ (no DB)
  │                            │                    │◄── "eyJhbGci..." ──│               │
  │                            │                    │── createRefreshToken ──────────────►│ (insert)
  │                            │                    │◄── raw UUID ───────│               │
  │◄── 200 AuthResponse ───────│                    │                    │               │
  │   {accessToken, refreshToken, user}             │                    │               │
  │                            │                    │                    │               │
  │── GET /api/v1/auth/me ─────►│                    │                    │               │
  │   Authorization: Bearer ... │                    │                    │               │
  │                    JwtFilter│                    │                    │               │
  │                       ├── isTokenValid ──────────────────────────────►│               │
  │                       ├── extractEmail ──────────────────────────────►│               │
  │                       ├── loadUser ─────────────────────────────────────────────────►│
  │                       └── setAuthentication     │                    │               │
  │                            │── getCurrentUser() ►│                    │               │
  │◄── 200 UserResponse ───────│                    │                    │               │
```
