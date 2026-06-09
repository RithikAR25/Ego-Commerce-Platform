/**
 * auth.types.ts
 *
 * TypeScript types for all auth-related API responses.
 * These mirror the backend DTOs in com.ego.raw_ego.auth.dto.response.
 *
 * IMPORTANT: Keep these in sync with the backend DTOs.
 * Field names must match exactly (camelCase, matching Jackson serialization).
 */

// ── Enums ─────────────────────────────────────────────────────────────────────

/**
 * User roles — matches UserRole enum in backend.
 * DB stores 'CUSTOMER'/'ADMIN' (uppercase after our fix in User.java).
 * JWT claim 'role' contains the enum name: 'CUSTOMER' or 'ADMIN'.
 */
export type UserRole = 'CUSTOMER' | 'ADMIN';

// ── Response types ─────────────────────────────────────────────────────────────

/**
 * Public user profile — matches com.ego.raw_ego.auth.dto.response.UserResponse.
 * Never includes password or sensitive fields.
 */
export interface UserResponse {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: UserRole;
  active: boolean;
  emailVerified: boolean;
  createdAt: string;   // ISO-8601 string (Instant serialized by Jackson 3.x)
  lastLoginAt: string | null;
}

/**
 * Full auth response after register/login/refresh.
 * Matches com.ego.raw_ego.auth.dto.response.AuthResponse.
 *
 * accessToken  → store in Zustand (in-memory) — NEVER localStorage
 * refreshToken → store in localStorage('ego_rt')
 * expiresIn    → seconds until accessToken expires (900 = 15 min)
 */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;       // seconds (900 = 15 min)
  tokenType: string;       // 'Bearer'
  user: UserResponse;
}

// ── Request types ─────────────────────────────────────────────────────────────

/** Matches com.ego.raw_ego.auth.dto.request.LoginRequest */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Matches com.ego.raw_ego.auth.dto.request.RegisterRequest */
export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone?: string;
}

/** Matches com.ego.raw_ego.auth.dto.request.RefreshTokenRequest */
export interface RefreshTokenRequest {
  refreshToken: string;
}
