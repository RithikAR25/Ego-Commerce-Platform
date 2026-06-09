/**
 * authStore.ts
 *
 * Zustand store for authentication state.
 *
 * Architecture decisions:
 *
 * 1. accessToken is IN-MEMORY ONLY.
 *    Rationale: XSS scripts can read localStorage but cannot read in-memory
 *    JS variables across contexts. This limits the attack window.
 *    If the tab closes, the AT is gone — on reload, the boot sequence uses
 *    the refresh token to silently get a new one.
 *
 * 2. refreshToken goes to localStorage('ego_rt').
 *    Rationale: Needs to survive page refreshes. The RT alone cannot authenticate
 *    — it only exchanges for a new AT+RT pair via /auth/refresh.
 *
 * 3. user profile is in Zustand (not TanStack Query).
 *    Rationale: Role checks (isAdmin) happen at the route guard level before
 *    TanStack Query is mounted. Having user in Zustand makes role checks synchronous.
 *
 * Backend integration:
 *    setTokens() is called by the Axios interceptor after /auth/refresh.
 *    setTokens() is also called directly by LoginForm and RegisterForm.
 *    clearAuth() is called by the Axios interceptor on refresh failure (redirect to login).
 */

import { create } from 'zustand';
import type { UserResponse } from '@/types/auth.types';

// ── Storage key ───────────────────────────────────────────────────────────────
export const RT_STORAGE_KEY = 'ego_rt';

// ── State interface ───────────────────────────────────────────────────────────
interface AuthState {
  /** JWT access token — IN-MEMORY ONLY. 15-minute TTL. Never localStorage. */
  accessToken: string | null;

  /** User profile loaded from /auth/me or embedded in login/register response. */
  user: UserResponse | null;

  /** True when accessToken is set. Used by ProtectedRoute and AdminRoute. */
  isAuthenticated: boolean;

  // ── Actions ────────────────────────────────────────────────────────────────

  /**
   * Called after successful login, register, or token refresh.
   * Stores AT in memory, RT in localStorage.
   */
  setTokens: (accessToken: string, refreshToken: string) => void;

  /** Updates the user profile (called after /auth/me or after profile update). */
  setUser: (user: UserResponse) => void;

  /**
   * Full logout — clears AT from memory and RT from localStorage.
   * Called by the Axios interceptor on unrecoverable 401,
   * and by the logout mutation in useAuth.ts.
   */
  clearAuth: () => void;
}

// ── Store ─────────────────────────────────────────────────────────────────────
export const useAuthStore = create<AuthState>()((set) => ({
  accessToken:     null,
  user:            null,
  isAuthenticated: false,

  setTokens: (accessToken, refreshToken) => {
    localStorage.setItem(RT_STORAGE_KEY, refreshToken);
    set({ accessToken, isAuthenticated: true });
  },

  setUser: (user) => set({ user }),

  clearAuth: () => {
    localStorage.removeItem(RT_STORAGE_KEY);
    set({ accessToken: null, user: null, isAuthenticated: false });
  },
}));
