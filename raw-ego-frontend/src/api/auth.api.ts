/**
 * auth.api.ts
 *
 * Auth API functions — thin wrappers around apiClient for all /auth endpoints.
 *
 * Rules:
 *   - No business logic here. Just HTTP calls + typed return values.
 *   - All functions return the raw AxiosResponse — hooks/useAuth.ts unwraps .data.data.
 *   - The /auth/refresh call inside the Axios interceptor does NOT use these functions
 *     (to avoid circular interceptor loops). It uses a raw axios.post() call.
 *
 * Backend endpoints (SecurityConfig.PUBLIC_MATCHERS):
 *   POST /api/v1/auth/register   → 201 Created
 *   POST /api/v1/auth/login      → 200 OK
 *   POST /api/v1/auth/refresh    → 200 OK
 *   POST /api/v1/auth/logout     → 200 OK  (requires valid AT for audit trail)
 *   GET  /api/v1/auth/me         → 200 OK  (requires valid AT)
 */

import apiClient from '@/api/client';
import type { ApiResponse }                               from '@/types/api.types';
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '@/types/auth.types';

// ── Auth endpoints ────────────────────────────────────────────────────────────

/**
 * Register a new CUSTOMER account.
 * Backend creates user with role=CUSTOMER by default (cannot self-register as ADMIN).
 */
export const register = (body: RegisterRequest) =>
  apiClient.post<ApiResponse<AuthResponse>>('/auth/register', body);

/**
 * Login with email + password.
 * Backend validates credentials, issues AT + RT pair.
 */
export const login = (body: LoginRequest) =>
  apiClient.post<ApiResponse<AuthResponse>>('/auth/login', body);

/**
 * Logout — revokes the specific refresh token in the DB.
 * The backend marks the RT as revoked_at = NOW(), invalidating the entire token family.
 * This endpoint requires a valid AT (for audit log actor tracking).
 */
export const logout = (refreshToken: string) =>
  apiClient.post<ApiResponse<null>>('/auth/logout', { refreshToken });

/**
 * Get current user's profile.
 * Used on app boot after silent refresh to populate authStore.user.
 * Also used by account/profile pages.
 */
export const getMe = () =>
  apiClient.get<ApiResponse<UserResponse>>('/auth/me');

/**
 * Verify email using the token sent to the user's email.
 */
export const verifyEmail = (token: string) =>
  apiClient.post<ApiResponse<null>>(`/auth/verify-email?token=${token}`);

/**
 * Resend the verification email to the currently logged in user.
 */
export const resendVerification = () =>
  apiClient.post<ApiResponse<null>>('/auth/resend-verification');

/**
 * Request a password reset email.
 * Always returns 200 — no indication of whether the email is registered (no enumeration).
 */
export const forgotPassword = (email: string) =>
  apiClient.post<ApiResponse<null>>('/auth/forgot-password', { email });

/**
 * Complete the password reset flow with the token from the email link.
 * Sets passwordChangedAt = now() on the server, invalidating all existing sessions.
 */
export const resetPassword = (token: string, newPassword: string) =>
  apiClient.post<ApiResponse<null>>('/auth/reset-password', { token, newPassword });
