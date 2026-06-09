# Security Architecture — EGO Platform

> **Framework:** Spring Security 7 (Spring Boot 4.0.6)
> **Model:** Stateless JWT — no sessions, no cookies (auth module)

---

## Security Filter Chain

```
Incoming HTTP Request
        │
        ▼
┌───────────────────┐
│   CorsFilter      │  ← Handles OPTIONS preflight; sets Access-Control-* headers
└────────┬──────────┘
         │
         ▼
┌──────────────────────────────────┐
│   JwtAuthenticationFilter        │  ← OncePerRequestFilter (custom)
│                                  │
│  1. Read Authorization header    │
│  2. Validate JWT signature+expiry│
│  3. Extract email + issuedAt     │
│  4. Load UserDetails from DB     │
│  5. passwordChangedAt guard      │  ← Instant AT invalidation after reset
│  6. Set SecurityContextHolder    │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│   AuthorizationFilter            │  ← Spring Security built-in
│                                  │
│  PUBLIC_MATCHERS → permitAll()   │
│  /admin/**       → ROLE_ADMIN    │
│  anyRequest      → authenticated │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│   Controller Method              │
│   @PreAuthorize (optional)       │  ← Fine-grained method-level guard
└──────────────────────────────────┘
```

### Error Paths

| Scenario | Handler | Response |
|----------|---------|----------|
| No/invalid token on protected route | `JwtAuthenticationEntryPoint` | 401 JSON |
| Valid token but wrong role | `JwtAccessDeniedHandler` | 403 JSON |
| @Valid failure | `GlobalExceptionHandler` | 400 JSON with field errors |
| Domain exception | `GlobalExceptionHandler` | HTTP status from exception |
| Unexpected exception | `GlobalExceptionHandler` | 500 JSON (no stack trace) |

---

## CSRF Strategy

**Status: DISABLED**

**Rationale:**
CSRF attacks work by tricking a browser into making a request using its stored session cookie. Our API uses:
1. **Stateless JWT** in `Authorization: Bearer` header — browsers never attach this automatically
2. **No session cookies** — `SessionCreationPolicy.STATELESS`

Therefore, CSRF protection provides zero additional security in this configuration.

**When to re-enable:** If refresh tokens are moved to httpOnly cookies (future hardening),
CSRF protection MUST be enabled using `SameSite=Strict` cookies or the Synchronizer Token pattern.

---

## CORS Configuration

```java
CorsConfiguration config = new CorsConfiguration();
config.setAllowedOriginPatterns(List.of(
    "http://localhost:*",      // local development (any port)
    "https://*.ego.com",       // production subdomains
    "https://ego.com"          // apex domain
));
config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
config.setAllowedHeaders(List.of("*"));
config.setExposedHeaders(List.of("Authorization", "X-Request-ID"));
config.setAllowCredentials(true);
config.setMaxAge(3600L);       // Preflight cached 1 hour
```

**Production update:** Override `allowedOriginPatterns` via environment variable in `docker-compose.yml`:
```yaml
environment:
  - CORS_ALLOWED_ORIGINS=https://ego.com,https://www.ego.com
```

---

## Password Security

| Property | Value | Reasoning |
|----------|-------|-----------|
| Algorithm | BCrypt | Adaptive — increase strength as hardware improves |
| Strength | 12 | ~250ms per hash on modern hardware — brute-force resistant |
| Max input | 72 chars | BCrypt silently truncates beyond 72 — validated in RegisterRequest |
| Comparison | `PasswordEncoder.matches()` | Timing-safe — constant-time comparison prevents timing attacks |
| Storage | Column `password_hash` | Column name avoids SQL reserved word conflicts |

---

## Security Headers (Phase 14 — Nginx)

These headers are NOT yet applied (Spring Boot defaults). Apply via Nginx in Phase 14:

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "default-src 'self'..." always;
```

---

## User Enumeration Prevention

| Attack | Mitigation |
|--------|-----------|
| Login: test if email exists | Same error for wrong email AND wrong password |
| Registration: confirm email taken | Returns 409 only — acceptable (known UX tradeoff) |
| Password reset: confirm email | Always returns "If that email exists, a reset link has been sent" |
| UserDetailsService | Logs at DEBUG level only — no business-logic leakage |

---

## Account Status Checks

```
isEnabled()        = active=true AND deleted=false
isAccountNonLocked() = active=true AND deleted=false
isAccountNonExpired()  = always true (we use is_active for suspension)
isCredentialsNonExpired() = always true (passwordChangedAt guard handles this)
```

Spring Security calls these during `DaoAuthenticationProvider` authentication.
Failed checks → `LockedException` or `DisabledException` → caught by `GlobalExceptionHandler`.

---

## Environment Variables Reference

| Variable | Default | Production Override |
|----------|---------|---------------------|
| `spring.jwt.secret` | (in properties) | `JWT_SECRET=<256-bit-hex>` |
| `spring.jwt.access-token-expiry-ms` | `900000` (15 min) | Adjust per security policy |
| `spring.jwt.refresh-token-expiry-days` | `30` | `7` for higher security |
| `spring.datasource.url` | `localhost:3307` | RDS/Cloud SQL endpoint |
| `spring.datasource.password` | `secret` | Secrets manager |
| `spring.jpa.hibernate.ddl-auto` | `update` | `validate` (production) |

---

## Threat Model Summary

| Threat | Countermeasure |
|--------|---------------|
| Token theft (AT) | Short 15-min expiry; `passwordChangedAt` guard |
| Token theft (RT) | Family-based reuse detection → full family revocation |
| Brute force login | BCrypt-12 (~250ms); future: rate limiting (Phase 14) |
| CSRF | Stateless JWT in header (not cookie) |
| XSS | Short-lived AT; advise in-memory storage (not localStorage) |
| SQL injection | JPA parameterized queries — zero raw SQL in application |
| Mass assignment | Explicit DTO fields with @Valid — no dynamic binding |
| Privilege escalation | Role stored in DB only; JWT embeds for stateless perf |
| User enumeration | Uniform error messages on login/reset |
| Stale tokens post-reset | `passwordChangedAt` column checked on every request |
