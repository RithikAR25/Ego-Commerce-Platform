# Authentication Module — EGO E-Commerce Platform

> **Module:** `com.ego.raw_ego.auth`
> **Phase:** 2 (Phase 1 = Docker/DB, Phase 2 = Auth)
> **Status:** ✅ Implemented

---

## Overview

The auth module provides complete JWT-based authentication and RBAC authorization for the EGO platform. It is a self-contained feature module under the `auth` package, with no circular dependencies.

## Package Structure

```
com.ego.raw_ego/
├── common/
│   ├── constants/AppConstants.java
│   ├── config/OpenApiConfig.java
│   ├── response/
│   │   ├── ApiResponse.java        ← standardized envelope: {success, message, data, errors, timestamp}
│   │   └── ApiError.java           ← per-field validation error
│   └── exception/
│       ├── EgoException.java       ← base: carries HttpStatus
│       ├── AuthException.java      ← 401 Unauthorized
│       ├── ResourceNotFoundException.java ← 404 Not Found
│       ├── ConflictException.java  ← 409 Conflict
│       └── GlobalExceptionHandler.java    ← @RestControllerAdvice
│
└── auth/
    ├── enums/UserRole.java         ← CUSTOMER, ADMIN (with Spring Security bridge)
    ├── entity/
    │   ├── User.java               ← implements UserDetails
    │   └── RefreshToken.java       ← token_hash + family_id lifecycle
    ├── repository/
    │   ├── UserRepository.java
    │   └── RefreshTokenRepository.java
    ├── dto/
    │   ├── request/
    │   │   ├── RegisterRequest.java
    │   │   ├── LoginRequest.java
    │   │   └── RefreshTokenRequest.java
    │   └── response/
    │       ├── UserResponse.java
    │       └── AuthResponse.java
    ├── service/
    │   ├── JwtService.java
    │   ├── RefreshTokenService.java
    │   └── AuthService.java
    ├── security/
    │   ├── SecurityConfig.java
    │   ├── JwtAuthenticationFilter.java
    │   ├── JwtAuthenticationEntryPoint.java
    │   ├── JwtAccessDeniedHandler.java
    │   └── UserDetailsServiceImpl.java
    └── controller/AuthController.java
```

---

## API Endpoints

| Method | Path | Auth | Description | Response |
|--------|------|------|-------------|----------|
| POST | `/api/v1/auth/register` | None | Create customer account | 201 AuthResponse |
| POST | `/api/v1/auth/login` | None | Login → token pair | 200 AuthResponse |
| POST | `/api/v1/auth/refresh` | None | Rotate refresh token | 200 AuthResponse |
| POST | `/api/v1/auth/logout` | Bearer | Revoke refresh token | 200 void |
| GET | `/api/v1/auth/me` | Bearer | Get current user profile | 200 UserResponse |

### Request Examples

**Register:**
```json
POST /api/v1/auth/register
{
  "firstName": "Rithik",
  "lastName": "A",
  "email": "rithik@ego.com",
  "password": "Secure@123",
  "phone": "+919876543210"
}
```

**Login:**
```json
POST /api/v1/auth/login
{
  "email": "rithik@ego.com",
  "password": "Secure@123"
}
```

**Refresh:**
```json
POST /api/v1/auth/refresh
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Logout:**
```json
POST /api/v1/auth/logout
Authorization: Bearer <access_token>
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Response Example

```json
{
  "success": true,
  "message": "Login successful.",
  "data": {
    "accessToken":  "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "tokenType":    "Bearer",
    "expiresIn":    900,
    "user": {
      "id": 1,
      "email": "rithik@ego.com",
      "firstName": "Rithik",
      "lastName": "A",
      "role": "CUSTOMER",
      "emailVerified": false,
      "active": true,
      "createdAt": "2026-05-21T09:00:00Z"
    }
  },
  "timestamp": "2026-05-21T09:00:00Z"
}
```

---

## Error Codes

| HTTP | Scenario |
|------|----------|
| 400 | Validation failure (email format, password policy, blank fields) |
| 401 | Wrong credentials, expired/invalid token, revoked token |
| 403 | Authenticated but insufficient role |
| 409 | Email already registered |
| 500 | Unexpected server error (never leaks stack trace) |

---

## Password Policy

- Minimum 8 characters, maximum 72 (BCrypt truncation limit)
- Must contain: uppercase, lowercase, digit
- Hashed with BCrypt strength 12 (~250ms per hash — brute-force resistant)

---

## Email Verification & Password Reset

> **Source-verified (June 6, 2026):** All these endpoints **ARE implemented** in `AuthController.java`. The frontend pages (`VerifyEmailPage`, `ForgotPasswordPage`, `ResetPasswordPage`) exist and wire to these endpoints. The previous session summary was incorrect — source code is the truth.

Email verification is **not enforced** at checkout (no guard blocking unverified users from placing orders). It is informational only in the current build.

---

## Full Endpoint List (Source-Verified from `AuthController.java`)

| Method | Path | Auth | Status | Description |
|--------|------|------|--------|-------------|
| `POST` | `/api/v1/auth/register` | None | ✅ Implemented | Create customer account |
| `POST` | `/api/v1/auth/login` | None | ✅ Implemented | Login → AT + RT pair |
| `POST` | `/api/v1/auth/refresh` | None | ✅ Implemented | RT rotation → new AT + RT |
| `POST` | `/api/v1/auth/logout` | Bearer | ✅ Implemented | Revoke RT + Redis blocklist AT |
| `GET` | `/api/v1/auth/me` | Bearer | ✅ Implemented | Get current user profile |
| `POST` | `/api/v1/auth/verify-email` | None (token param) | ✅ Implemented | Validate 24h JWT, set emailVerified=true |
| `POST` | `/api/v1/auth/resend-verification` | Bearer | ✅ Implemented | Re-send verification email |
| `POST` | `/api/v1/auth/forgot-password` | None | ✅ Implemented | Send 1h reset link (always 200 — no enumeration) |
| `POST` | `/api/v1/auth/reset-password` | None (token body) | ✅ Implemented | Validate RESET JWT, hash new password, set passwordChangedAt=now |
| `POST` | `/api/v1/auth/logout-all` | ⚠️ Not implemented | Revoke all RT families | `RefreshTokenRepository.revokeAllByUser()` not found in source |

> **CONTRADICTION FIXED:** Earlier documentation marked verify-email, forgot-password, and reset-password as "not implemented". `AuthController.java` source proves all are implemented. Only `logout-all` is unimplemented.
