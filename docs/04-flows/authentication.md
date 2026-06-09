# Authentication Flow

> All values verified against source: `JwtService.java`, `RefreshTokenService.java`, `JwtAuthenticationFilter.java`, `client.ts`.

---

## Registration & Login

```mermaid
sequenceDiagram
    participant U as User
    participant FE as Frontend
    participant BE as Backend
    participant DB as MySQL

    U->>FE: POST /register {email, password, firstName, lastName}
    FE->>BE: POST /api/v1/auth/register
    BE->>DB: Check email uniqueness
    BE->>BE: BCrypt-12 hash password
    BE->>DB: INSERT user (is_active=true, is_email_verified=false)
    BE-->>FE: 201 {accessToken, refreshToken, user}

    Note over FE: Store accessToken in Zustand (memory)<br/>Store refreshToken in localStorage['ego_rt']

    U->>FE: POST /login {email, password}
    FE->>BE: POST /api/v1/auth/login
    BE->>DB: Load user by email
    BE->>BE: BCrypt.matches(input, stored_hash)
    BE->>DB: Insert refresh_tokens row (SHA-256 hash, family_id)
    BE-->>FE: 200 {accessToken, refreshToken, user}
```

---

## App Boot (Persisted Session Restore)

```mermaid
sequenceDiagram
    participant Browser
    participant Main as main.tsx
    participant AS as authStore
    participant BE as Backend

    Browser->>Main: App loads
    Main->>Main: Read localStorage['ego_rt']
    alt RT exists
        Main->>BE: POST /api/v1/auth/refresh {refreshToken}
        BE->>BE: SHA-256 hash RT, find in DB
        BE->>BE: Validate not revoked, not expired
        BE->>BE: Rotate RT (mark old revoked, issue new RT)
        BE-->>Main: {accessToken, refreshToken, user}
        Main->>AS: setAccessToken(at), setUser(user)
        Main->>Main: Store new RT in localStorage
    else No RT
        Main->>AS: clearAuth()
        Note over Main: User is not authenticated
    end
```

---

## Silent AT Refresh (401 Interceptor)

```mermaid
sequenceDiagram
    participant R1 as Request 1
    participant R2 as Request 2 (concurrent)
    participant I as Axios Interceptor
    participant BE as Backend

    R1->>I: Response: 401 (AT expired)
    R2->>I: Response: 401 (same, concurrent)

    I->>I: isRefreshing = false → set true
    I->>I: Queue R2 in failedQueue

    I->>BE: POST /api/v1/auth/refresh {RT}
    BE-->>I: {newAT, newRT}

    I->>I: Store newAT in authStore
    I->>I: Store newRT in localStorage
    I->>I: isRefreshing = false

    I->>BE: Retry R1 with newAT
    I->>BE: Retry R2 with newAT (from queue)

    Note over I: Without queue: R2 would attempt refresh<br/>while R1's refresh is in flight, presenting<br/>the rotated (revoked) RT → family revocation!
```

---

## Token Theft Detection

```mermaid
sequenceDiagram
    participant T as Attacker (has stolen RT)
    participant L as Legitimate User
    participant BE as Backend

    L->>BE: POST /auth/refresh {RT_v1}
    BE->>BE: Rotate: RT_v1 → RT_v2 (mark RT_v1 revoked)
    BE-->>L: {AT, RT_v2}

    T->>BE: POST /auth/refresh {RT_v1} [replay with old RT]
    BE->>BE: SHA-256 hash → find RT_v1 in DB
    BE->>BE: RT_v1 is REVOKED → THEFT DETECTED
    BE->>BE: Revoke entire family (all RTs with same family_id)
    BE-->>T: 401 Unauthorized

    L->>BE: POST /auth/refresh {RT_v2}
    BE->>BE: RT_v2 is now also REVOKED (family revoked)
    BE-->>L: 401 Unauthorized (must re-login)
```

---

## `passwordChangedAt` Guard

```mermaid
flowchart TD
    REQ[HTTP Request with AT] --> JWTFilter[JwtAuthenticationFilter]
    JWTFilter --> ParseAT{Parse AT claims}
    ParseAT -->|Invalid signature| Reject401[401]
    ParseAT -->|Expired| Reject401
    ParseAT -->|Valid| LoadUser[Load User from DB]
    LoadUser --> ChangedCheck{token.issuedAt < user.passwordChangedAt?}
    ChangedCheck -->|Yes — AT predates password change| Reject401
    ChangedCheck -->|No — AT is valid| SetContext[Set SecurityContext]
    SetContext --> Controller[Proceed to Controller]
```

---

## RBAC — Route Access Control

```mermaid
flowchart LR
    Request --> SC[SecurityConfig filter chain]
    SC --> PM{In PUBLIC_MATCHERS?}
    PM -->|Yes| Allow[permitAll]
    PM -->|No| Auth{Authenticated?}
    Auth -->|No| 401
    Auth -->|Yes| Admin{Path /admin/**?}
    Admin -->|Yes| Role{ROLE_ADMIN?}
    Role -->|Yes| Allow
    Role -->|No| 403
    Admin -->|No| Allow
```

**PUBLIC_MATCHERS (no auth required):**
- `GET /api/v1/categories/**`
- `GET /api/v1/products/**`
- `GET /api/v1/search`, `GET /api/v1/search/autocomplete`
- `GET /api/v1/coupons/validate`
- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`
- `POST /api/v1/webhooks/razorpay`
- `GET /api/v1/products/{id}/reviews`, `GET /api/v1/products/{id}/reviews/summary`
- `/docs/**` (Swagger UI)

**Requires ROLE_ADMIN:** All `/api/v1/admin/**` paths
