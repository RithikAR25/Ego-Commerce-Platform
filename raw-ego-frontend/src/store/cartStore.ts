/**
 * cartStore.ts
 *
 * Zustand store for lightweight cart UI state.
 *
 * Architecture decisions:
 *
 * 1. Full cart data (items, subtotal) lives in TanStack Query — it's server state.
 *    This store ONLY holds what TanStack Query cannot: the badge count (derived from
 *    the last known cart response) and the anonymous session ID.
 *
 * 2. sessionId is a UUID generated once per browser session and persisted to
 *    localStorage under 'ego_session_id'. It identifies the anonymous cart in Redis
 *    (key: "cart:anon:{sessionId}") before the user logs in.
 *    After login, the frontend calls POST /cart/merge to fold the anonymous cart
 *    into the authenticated user's cart.
 *
 * 3. itemCount is synced from TanStack Query hooks (useCart) via setItemCount().
 *    This prevents re-mounting the Navbar from losing the badge count between
 *    route transitions where the cart query might not be active.
 */

import { create } from 'zustand';

// ── Storage keys ───────────────────────────────────────────────────────────────
export const SESSION_ID_STORAGE_KEY = 'ego_session_id';

// ── State interface ────────────────────────────────────────────────────────────
interface CartState {
  /**
   * Total unit count across all cart lines — drives the Navbar badge.
   * Updated by useCart hook on every successful cart query/mutation.
   */
  itemCount: number;
  setItemCount: (count: number) => void;

  /**
   * Anonymous session identifier — UUID generated once, stored in localStorage.
   * Sent to POST /cart/merge on login.
   */
  sessionId: string;

  /** Resets item count to 0 (called on logout). */
  resetCart: () => void;
}

// ── Session ID initializer ─────────────────────────────────────────────────────

/**
 * Gets or creates the anonymous session ID from localStorage.
 * This runs once at store initialization time.
 */
const getOrCreateSessionId = (): string => {
  const existing = localStorage.getItem(SESSION_ID_STORAGE_KEY);
  if (existing) return existing;
  const newId = crypto.randomUUID();
  localStorage.setItem(SESSION_ID_STORAGE_KEY, newId);
  return newId;
};

// ── Store ──────────────────────────────────────────────────────────────────────
export const useCartStore = create<CartState>()((set) => ({
  itemCount: 0,
  sessionId: getOrCreateSessionId(),

  setItemCount: (count) => set({ itemCount: count }),

  resetCart: () => set({ itemCount: 0 }),
}));
