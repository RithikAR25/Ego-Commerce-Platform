/**
 * useAuth.ts
 *
 * TanStack Query hooks for authentication.
 *
 * Exports:
 *   useCurrentUser()  → query for /auth/me (current logged-in user profile)
 *   useLogin()        → mutation: login → setTokens → setUser → navigate
 *   useRegister()     → mutation: register → setTokens → setUser → navigate
 *   useLogout()       → mutation: logout → clearAuth → clear query cache → navigate
 *
 * Design decisions:
 *   - Business logic (authStore updates, navigation) is here in the hook,
 *     NOT in the component. Components only call mutate() and handle UI states.
 *   - useCurrentUser is only enabled when isAuthenticated=true (no wasted request).
 *   - useLogout clears the entire TanStack Query cache so stale user data
 *     cannot bleed into a new session.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { authKeys }    from '@/hooks/queryKeys';
import { useAuthStore } from '@/store/authStore';
import { toast }        from '@/store/uiStore';
import { extractApiError } from '@/utils/apiError';
import * as authApi    from '@/api/auth.api';
import { mergeCart }   from '@/api/cart.api';
import { useCartStore, SESSION_ID_STORAGE_KEY } from '@/store/cartStore';
import { cartKeys }    from '@/features/cart/hooks/useCart';
import type { LoginFormData, RegisterFormData } from '@/schemas/auth.schema';

// ── Current user query ────────────────────────────────────────────────────────

/**
 * Fetches the current user's profile from /auth/me.
 * Enabled only when the user is authenticated (has an access token).
 * Used by the account page and anywhere you need fresh user data.
 */
export const useCurrentUser = () => {
  const { isAuthenticated, setUser } = useAuthStore();

  return useQuery({
    queryKey: authKeys.me(),
    queryFn:  async () => {
      const { data } = await authApi.getMe();
      // Keep authStore.user in sync with fresh server data
      setUser(data.data);
      return data.data;
    },
    enabled:   isAuthenticated,
    staleTime: 5 * 60_000,   // 5 min — profile data is stable
    gcTime:    30 * 60_000,
  });
};

// ── Login mutation ────────────────────────────────────────────────────────────

/**
 * Login mutation.
 *
 * Flow:
 *   1. POST /auth/login
 *   2. Store AT (memory) + RT (localStorage)
 *   3. Store user profile in authStore
 *   4. Navigate to redirect param or home
 *
 * On error: return field-level errors for form to display inline.
 */
export const useLogin = (options?: { onFieldError?: (field: string, msg: string) => void }) => {
  const { setTokens, setUser } = useAuthStore();
  const navigate = useNavigate();
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: LoginFormData) => authApi.login(data),

    onSuccess: async (response) => {
      const { accessToken, refreshToken, user } = response.data.data;
      setTokens(accessToken, refreshToken);
      setUser(user);
      // Seed the 'auth/me' cache with the user from the login response
      qc.setQueryData(authKeys.me(), user);
      toast.success(`Welcome back, ${user.firstName}!`);

      // Merge anonymous cart into user cart (best-effort — non-critical)
      const sessionId = localStorage.getItem(SESSION_ID_STORAGE_KEY);
      if (sessionId) {
        try {
          const mergedCart = await mergeCart({ sessionId });
          qc.setQueryData(cartKeys.cart(), mergedCart);
          useCartStore.getState().setItemCount(mergedCart.itemCount);
        } catch {
          // Merge failure is non-critical — user cart still loads on next visit
          qc.invalidateQueries({ queryKey: cartKeys.all });
        }
      }

      // Navigate to the redirect param if present (e.g. /login?redirect=/checkout)
      const params = new URLSearchParams(window.location.search);
      const redirectTo = params.get('redirect') ?? '/';
      navigate(redirectTo, { replace: true });
    },

    onError: (error) => {
      const { message, fieldErrors } = extractApiError(error);
      if (fieldErrors && Object.keys(fieldErrors).length > 0) {
        // Show each field-level error inline on the form
        Object.entries(fieldErrors).forEach(([field, msg]) => {
          options?.onFieldError?.(field, msg);
        });
      } else {
        // Generic auth error (e.g. 401 bad credentials) — show in the form's root alert
        // Fallback to toast if no form handler is registered
        if (options?.onFieldError) {
          options.onFieldError('root', message);
        } else {
          toast.error(message);
        }
      }
    },
  });
};

// ── Register mutation ─────────────────────────────────────────────────────────

/**
 * Register mutation.
 * Same post-success flow as login — user is immediately authenticated.
 */
export const useRegister = (options?: { onFieldError?: (field: string, msg: string) => void }) => {
  const { setTokens, setUser } = useAuthStore();
  const navigate = useNavigate();
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (data: RegisterFormData) => authApi.register({
      firstName: data.firstName,
      lastName:  data.lastName,
      email:     data.email,
      password:  data.password,
      phone:     data.phone || undefined,
    }),

    onSuccess: async (response) => {
      const { accessToken, refreshToken, user } = response.data.data;
      setTokens(accessToken, refreshToken);
      setUser(user);
      qc.setQueryData(authKeys.me(), user);
      toast.success(`Welcome to EGO, ${user.firstName}!`);

      // Merge anonymous cart into user cart (best-effort — non-critical)
      const sessionId = localStorage.getItem(SESSION_ID_STORAGE_KEY);
      if (sessionId) {
        try {
          const mergedCart = await mergeCart({ sessionId });
          qc.setQueryData(cartKeys.cart(), mergedCart);
          useCartStore.getState().setItemCount(mergedCart.itemCount);
        } catch {
          qc.invalidateQueries({ queryKey: cartKeys.all });
        }
      }

      navigate('/', { replace: true });
    },

    onError: (error) => {
      const { message, fieldErrors } = extractApiError(error);
      if (fieldErrors && Object.keys(fieldErrors).length > 0) {
        // Show each field-level error inline on the form
        Object.entries(fieldErrors).forEach(([field, msg]) => {
          options?.onFieldError?.(field, msg);
        });
      } else {
        // Generic error — surface in the form's root alert if handler exists
        if (options?.onFieldError) {
          options.onFieldError('root', message);
        } else {
          toast.error(message);
        }
      }
    },
  });
};

// ── Logout mutation ───────────────────────────────────────────────────────────

/**
 * Logout mutation.
 *
 * Flow:
 *   1. POST /auth/logout with current RT (backend revokes the token in DB)
 *   2. clearAuth() — wipes AT + RT
 *   3. queryClient.clear() — removes all cached data so next user gets a clean slate
 *   4. Navigate to /login
 *
 * NOTE: Even if the API call fails (network error), we still clear local state.
 * A failed logout API call is better than a user stuck logged in.
 */
export const useLogout = () => {
  const { clearAuth } = useAuthStore();
  const navigate = useNavigate();
  const qc = useQueryClient();

  return useMutation({
    mutationFn: () => {
      const rt = localStorage.getItem('ego_rt');
      return rt ? authApi.logout(rt) : Promise.resolve(null);
    },

    onSettled: () => {
      // Order matters:
      // 1. Clear query cache FIRST — so no stale query refetches fire after clearAuth
      // 2. clearAuth() — wipes AT from memory + RT from localStorage
      // 3. Reset cart badge count
      // 4. Navigate to home — NOT /login, so ProtectedRoute cannot race and add ?redirect
      qc.clear();
      clearAuth();
      useCartStore.getState().resetCart();
      navigate('/', { replace: true });
    },
  });
};
