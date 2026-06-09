# ADR-003: JWT + Refresh Token Rotation Strategy

**Status:** Accepted  
**Date:** May 2026  
**Decision-makers:** Engineering Team  
**Implemented by:** Antigravity AI Agent

---

## Context

EGO is a stateless REST API serving both a browser SPA and potential future mobile clients. The authentication mechanism must balance:
- **Security:** Short-lived access tokens to limit breach window
- **UX:** Users should not be logged out mid-session
- **Theft detection:** Stolen tokens should be detectable and revocable
- **Scalability:** No server-side session state (stateless for horizontal scaling)

---

## Decision

**Stateless JWT + Refresh Token Rotation with Family-Based Theft Detection**

### Token Architecture

| Token | Storage | Expiry | Revocable |
|---|---|---|---|
| Access Token (AT) | In-memory (Zustand) | 15 minutes | No (by design — stateless) |
| Refresh Token (RT) | `localStorage['ego_rt']` | 30 days | Yes — SHA-256 hash stored in DB |

### AT Design
- Signed with HMAC-SHA256 (`JWT_SECRET` — 256-bit hex key)
- Claims: `sub` (email), `iat`, `exp`, `role`
- **Never persisted** — verification is pure cryptographic (no DB lookup)
- `passwordChangedAt` guard: If `token.issuedAt < user.passwordChangedAt`, the token is rejected. This instantly invalidates all outstanding ATs after a password change without maintaining a blocklist.

### RT Design
- Random UUID, SHA-256 hashed before storage in `refresh_tokens` table
- `family_id` (UUID) groups all RTs issued from a single login session
- On rotation: old RT marked `revoked=true`, new RT issued with same `family_id`
- **Theft detection:** If a revoked RT is presented again (replay attack), the entire family is revoked — all sessions from that login are terminated

### Frontend Interceptor (Queue Pattern)
When the AT expires, multiple concurrent requests may hit 401 simultaneously. Without the queue pattern, each would trigger a separate refresh — causing family revocation (each refresh rotates the RT, so the second refresh attempt presents the already-rotated-and-revoked old RT).

```typescript
// Only ONE refresh in flight at a time
// Others wait in the queue and retry with the new AT
let isRefreshing = false;
let failedQueue: Array<{ resolve, reject }> = [];
```

---

## Alternatives Considered

### A. Sessions (HttpOnly cookies + server-side session store)
- **Rejected:** Requires Redis session store (added infra) or sticky sessions (limits scaling). Complicates mobile/API client integration. CSRF protection mandatory.

### B. Short-lived AT only (no RT)
- **Rejected:** AT expiry of 15min means user must re-login every 15min. Unacceptable UX.

### C. Long-lived AT (no expiry)
- **Rejected:** Stolen AT is valid until password change. No recovery mechanism for compromised tokens.

### D. Store AT in localStorage
- **Rejected:** XSS vulnerability — malicious script can read `localStorage`. In-memory storage (Zustand) is not accessible by injected scripts.

### E. HttpOnly cookie for RT
- **Future consideration:** More secure (inaccessible to JavaScript) but requires CSRF protection and complicates CORS. Current `localStorage` RT trade-off is acceptable given RT alone cannot authenticate — it only exchanges for a new AT via a server call.

### F. Redis-backed token blocklist
- **Rejected for AT:** Adds Redis dependency to every authenticated request (N DB lookups for N requests). The `passwordChangedAt` guard achieves the most critical use case (post-reset invalidation) without per-request DB lookups.
- Redis blocklist is reconsidered only if `logout-all-sessions` becomes a requirement.

---

## Consequences

### Positive
- Stateless — no session state, horizontal scaling trivial
- 15-min AT expiry limits breach window significantly
- Family revocation detects token theft automatically
- `passwordChangedAt` guard handles post-reset invalidation without blocklist
- Queue pattern prevents accidental family revocation on concurrent requests

### Negative / Risks
- RT in `localStorage` is XSS-accessible. Mitigated by: short AT lifetime, HtmlSanitizer on all user input, strict CSP (planned Phase 14)
- Cannot instantly revoke an AT (within its 15-min window). Mitigated by: short expiry, `passwordChangedAt` guard

### Known Limitation
- `logout-all-sessions` (revoke all families for a user) is not implemented — noted as future work in `authentication-module.md`

---

## Source References
- `com.ego.raw_ego.auth.service.JwtService` — AT issuance, validation, `passwordChangedAt` check
- `com.ego.raw_ego.auth.service.RefreshTokenService` — RT rotation, family revocation
- `com.ego.raw_ego.auth.entity.RefreshToken` — `tokenHash`, `familyId`, `revoked`
- `raw-ego-frontend/src/api/client.ts` — Axios interceptor queue pattern
- `raw-ego-frontend/src/store/authStore.ts` — in-memory AT storage
- `docs/security/refresh-token-architecture.md` — archived detailed RT architecture doc
