/**
 * main.tsx
 *
 * Application entry point.
 *
 * Boot sequence (runs before React renders anything):
 *
 *   1. Check localStorage for 'ego_rt' (refresh token).
 *   2. If found: call POST /auth/refresh silently.
 *      - Success: store new AT in authStore, new RT in localStorage.
 *                 User stays logged in — no login flash.
 *      - Failure: clear auth. User sees public app (not forced to /login).
 *                 They'll be redirected to /login when they try to access
 *                 a protected route.
 *   3. If not found: user is a guest. Render normally.
 *   4. Mount React with AppProviders (Theme + QueryClient).
 *
 * Why do this BEFORE React renders?
 *   If we let React render first and THEN do the refresh, a user with a valid
 *   refresh token would briefly see the unauthenticated state (login button,
 *   no cart count, no "My Account" menu) before the refresh completes.
 *   Doing it in main.tsx ensures the auth state is correct from frame 1.
 */

import React from 'react';
import ReactDOM from 'react-dom/client';
import axios from 'axios';
import { AppProviders }  from '@/providers/AppProviders';
import { useAuthStore, RT_STORAGE_KEY } from '@/store/authStore';
import type { UserResponse } from '@/types/auth.types';
import App from './App';
import './index.css';

// ── Silent boot refresh ───────────────────────────────────────────────────────

const bootRefresh = async (): Promise<void> => {
  const rt = localStorage.getItem(RT_STORAGE_KEY);
  if (!rt) return;   // No token — guest user, nothing to do

  const apiBase = import.meta.env.VITE_API_BASE_URL as string;

  // ── Step 1: Exchange RT for a fresh AT + RT pair ─────────────────────────
  // If this fails → RT is expired/revoked/invalid → clear auth + proceed as guest.
  let accessToken: string;
  try {
    const refreshRes = await axios.post(
      `${apiBase}/auth/refresh`,
      { refreshToken: rt },
      { headers: { 'Content-Type': 'application/json' }, timeout: 8_000 },
    );

    const tokens = refreshRes.data.data as { accessToken: string; refreshToken: string };
    accessToken = tokens.accessToken;

    // Store tokens immediately — isAuthenticated becomes true
    useAuthStore.getState().setTokens(accessToken, tokens.refreshToken);
  } catch {
    // RT is expired, revoked, or invalid → clear auth, proceed as guest.
    // ProtectedRoute will redirect to /login when they visit a protected page.
    useAuthStore.getState().clearAuth();
    return;
  }

  // ── Step 2: Fetch user profile with the new AT ────────────────────────────
  // SEPARATE try/catch: a server error (500) here does NOT mean auth is broken.
  // The tokens are valid. User profile will be fetched lazily by useCurrentUser().
  // Only skips the pre-render hydration — no 'U' flash on server errors.
  try {
    const meRes = await axios.get(`${apiBase}/auth/me`, {
      headers: {
        'Content-Type':  'application/json',
        'Authorization': `Bearer ${accessToken}`,
      },
      timeout: 8_000,
    });

    const user = meRes.data.data as UserResponse;
    useAuthStore.getState().setUser(user);
  } catch {
    // /auth/me failed (server error or network issue) — tokens remain valid.
    // useCurrentUser() will retry when the AccountPage or Navbar mounts.
    console.warn('[boot] /auth/me failed — user profile will be loaded lazily.');
  }
};

// ── Mount ─────────────────────────────────────────────────────────────────────

const mount = async () => {
  // Run boot sequence before rendering
  await bootRefresh();

  // Render app
  const root = document.getElementById('root');
  if (!root) throw new Error('Root element not found');

  ReactDOM.createRoot(root).render(
    <React.StrictMode>
      <AppProviders>
        <App />
      </AppProviders>
    </React.StrictMode>,
  );
};

mount();
