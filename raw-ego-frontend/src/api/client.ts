/**
 * client.ts
 *
 * Central Axios instance for the entire EGO frontend.
 *
 * Architecture:
 *   - ONE instance shared across all API modules.
 *   - Request interceptor: attaches JWT access token from authStore.
 *   - Response interceptor: handles 401 with queue-based token refresh.
 *
 * The Queue Pattern (why it matters):
 *   If 5 API calls fire simultaneously and the AT expires, naively all 5 would
 *   try to call /auth/refresh. The backend uses refresh token ROTATION + FAMILY REUSE
 *   detection. If the same RT is used twice, the backend revokes the entire family
 *   (theft detection). So we MUST ensure only ONE refresh happens at a time.
 *
 *   Implementation:
 *     isRefreshing flag    → prevents parallel refresh calls
 *     failedQueue array    → holds { resolve, reject } for requests that hit 401 while
 *                           a refresh is already in progress
 *     drainQueue()         → on success: retries all queued requests with new token
 *                          → on failure: rejects all queued requests
 *
 * Backend contracts:
 *   POST /api/v1/auth/refresh { refreshToken: string }
 *   → 200 ApiResponse<AuthResponse> on success
 *   → 401 on expired/revoked/reused refresh token
 *
 * Key Axios 1.x type notes:
 *   - error.config is InternalAxiosRequestConfig (headers: AxiosHeaders — never undefined)
 *   - Use headers.set() not bracket notation for proper AxiosHeaders mutation
 *   - Delete the stale Authorization header before retry so the request interceptor
 *     cleanly re-attaches the new token from the store
 */

import axios, { type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore, RT_STORAGE_KEY } from '@/store/authStore';
import { toast } from '@/store/uiStore';

// ── Dev-mode logger ───────────────────────────────────────────────────────────

const isDev = import.meta.env.DEV;
const log = (...args: unknown[]) => isDev && console.log('[apiClient]', ...args);
const warn = (...args: unknown[]) => isDev && console.warn('[apiClient]', ...args);

// ── Extended config type ──────────────────────────────────────────────────────

type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

// ── Create instance ───────────────────────────────────────────────────────────

const apiClient = axios.create({
  baseURL:         import.meta.env.VITE_API_BASE_URL as string,
  timeout:         15_000,
  headers:         { 'Content-Type': 'application/json' },
  withCredentials: false,   // No cookies — we manage tokens manually
});

// ── Refresh queue state ───────────────────────────────────────────────────────

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject:  (error: unknown) => void;
}> = [];

const drainQueue = (token: string | null, error: unknown = null) => {
  failedQueue.forEach((p) => {
    if (token) p.resolve(token);
    else       p.reject(error);
  });
  failedQueue = [];
};

// ── Request interceptor ───────────────────────────────────────────────────────
// Attaches the current access token on every request (if available).

apiClient.interceptors.request.use(
  (config) => {
    const { accessToken } = useAuthStore.getState();
    if (accessToken) {
      config.headers.set('Authorization', `Bearer ${accessToken}`);
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ── Response interceptor ──────────────────────────────────────────────────────

apiClient.interceptors.response.use(
  // Success path — pass through
  (response) => response,

  // Error path
  async (error) => {
    // Guard: error.config can be undefined on network errors
    if (!error.config) return Promise.reject(error);

    const originalRequest = error.config as RetryableConfig;

    // ── Handle 401: attempt silent token refresh ─────────────────────────────
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      // Never try to refresh on auth-related endpoints themselves
      !originalRequest.url?.includes('/auth/refresh') &&
      !originalRequest.url?.includes('/auth/login') &&
      !originalRequest.url?.includes('/auth/register')
    ) {
      log(`401 on [${originalRequest.method?.toUpperCase()} ${originalRequest.url}] — attempting silent refresh`);

      // Another refresh is already in progress — queue this request
      if (isRefreshing) {
        log('Refresh in progress — queuing request');
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            // Remove stale token; request interceptor will attach the fresh one
            originalRequest.headers.set('Authorization', `Bearer ${token}`);
            return apiClient(originalRequest);
          })
          .catch(Promise.reject.bind(Promise));
      }

      // Mark this request so it doesn't loop on retry
      originalRequest._retry = true;
      isRefreshing = true;

      const rt = localStorage.getItem(RT_STORAGE_KEY);

      if (!rt) {
        // No refresh token — session is gone
        warn('No refresh token in localStorage — clearing auth and redirecting');
        isRefreshing = false;
        drainQueue(null, error);
        useAuthStore.getState().clearAuth();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      try {
        log('Calling /auth/refresh...');

        // Call refresh endpoint with raw axios — NOT apiClient — to avoid
        // triggering this interceptor recursively.
        const { data } = await axios.post(
          `${import.meta.env.VITE_API_BASE_URL as string}/auth/refresh`,
          { refreshToken: rt },
          {
            headers: { 'Content-Type': 'application/json' },
            timeout: 10_000,
          },
        );

        // Backend: { success, message, data: AuthResponse }
        // data.data = AuthResponse = { accessToken, refreshToken, ... }
        const { accessToken: newAt, refreshToken: newRt } = data.data as {
          accessToken: string;
          refreshToken: string;
        };

        log('Token refresh successful — new AT received');

        // Persist new tokens: AT → Zustand memory, RT → localStorage
        useAuthStore.getState().setTokens(newAt, newRt);

        // Drain the queue — all waiting requests get the new token
        drainQueue(newAt, null);

        // Remove the old stale Authorization header.
        // The request interceptor will re-attach the fresh token from the store.
        originalRequest.headers.delete('Authorization');

        return apiClient(originalRequest);
      } catch (refreshError) {
        // Refresh failed: expired/revoked/reused RT → force logout
        warn('Token refresh failed — clearing auth and redirecting to /login', refreshError);
        drainQueue(null, refreshError);
        useAuthStore.getState().clearAuth();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // ── Handle 403: user doesn't have the required role ──────────────────────
    if (error.response?.status === 403) {
      warn('403 Forbidden — redirecting to /403');
      window.location.href = '/403';
      return Promise.reject(error);
    }

    // ── Handle 500+: show generic toast ──────────────────────────────────────
    if (error.response?.status >= 500) {
      toast.error('Something went wrong. Please try again.');
    }

    return Promise.reject(error);
  },
);

export default apiClient;
