# Frontend Context & Architecture — EGO Platform

This document describes the design patterns, UI systems, state divisions, and API integrations of the EGO frontend client (`raw-ego-frontend`).

---

## 1. Technical Framework

The frontend is a single-page application (SPA) built using **React 18**, **TypeScript**, **Vite**, and **Material-UI (MUI) v5**.

```
              ┌─────────────────────────────────────────┐
              │             Index / main.tsx            │
              │  (AppProviders, Theme, GlobalToasts)    │
              └────────────────────┬────────────────────┘
                                   │
                                   ▼
              ┌─────────────────────────────────────────┐
              │          React Router Switch            │
              │  (Protected Guards / Storefront / Admin)│
              └────────────────────┬────────────────────┘
                                   │
                                   ▼
        ┌──────────────────────────┴──────────────────────────┐
        │                                                     │
        ▼                                                     ▼
┌───────────────────────────────┐             ┌───────────────────────────────┐
│        Storefront Views       │             │       Administrative Panels   │
│  - Landing & PDP Selector     │             │  - Catalog management CRUD   │
│  - Checkout & Razorpay Frame  │             │  - Metrics & Inventory levels │
└───────────────┬───────────────┘             └───────────────┬───────────────┘
                │                                             │
                └──────────────┬──────────────────────────────┘
                               │
                               ▼
        ┌─────────────────────────────────────────────────────┐
        │                 Shared Core Hooks                   │
        │      (useAuth, useCart, useCheckout, useProducts)   │
        └──────────────────────┬──────────────────────────────┘
                               │
                               ▼
        ┌─────────────────────────────────────────────────────┐
        │                 State & Cache Layers                │
        │  1. TanStack Query (Backend Server Cache)           │
        │  2. Zustand Stores (Local Sync State: Auth, UI)      │
        └──────────────────────┬──────────────────────────────┘
                               │
                               ▼
        ┌─────────────────────────────────────────────────────┐
        │             Axios Interceptor client.ts             │
        │    (Request Bearer, Response Queued Refresh)        │
        └─────────────────────────────────────────────────────┘
```

---

## 2. Router & Security Guards

Client-side navigation is managed via `react-router-dom`:
* **Storefront Routes:** Public views (e.g., Landing, PDP, Category listings, Cart) accessible without authentication.
* **Protected Guards (`ProtectedRoute`):** Evaluates `isAuthenticated` and `user.role` from the `authStore`. Unauthenticated requests redirect to `/login`.
* **Privilege Leveling:** Administrators are routed to `/admin` dashboard setups. Customers attempting to enter administrative views are intercepted and redirected to a `403 Forbidden` fallback view.
* **Logout Navigation:** Executed in the sequence: `qc.clear()` (invalidates query cache) → `clearAuth()` (resets Zustand states) → `navigate('/')` (navigates back to the landing view). This prevents data leakage and races during route switches.

---

## 3. Session Integration & Refresh Interceptors

Stateful token tracking operates on a hybrid storage strategy:
* **Access Tokens (AT):** Reside strictly in-memory (`authStore`) for security against XSS.
* **Refresh Tokens (RT):** Saved inside `localStorage` under the unique key `ego_rt` to persist sessions across page reloads.

### The Response Interceptor Silent Refresh Flow
The primary Axios client (`src/api/client.ts`) implements an atomic **Queued Refresh Interceptor**:
1. **Anomaly Catching:** A `401 Unauthorized` return from protected routes triggers the refresh flow (excluding `/login`, `/register`, and `/refresh` endpoints).
2. **Refresh Queue Lock:** If a token refresh operation is already in progress, additional incoming requests are pushed to a `failedQueue` array, returning a pending Promise.
3. **Rotation Dispatch:** The first request sets `isRefreshing = true` and fires a `POST /auth/refresh` using a raw Axios instance.
4. **Queue Drainage:**
   * **On Success:** Saves the rotated AT/RT pair. Resolves all waiting promises in `failedQueue`, replaying the original API requests with the new AT.
   * **On Failure:** Clears the local session via `clearAuth()`, rejects queued requests, and redirects to `/login`.

---

## 4. State Division: Zustand vs. TanStack Query

To keep components lightweight, state is strictly split between local client state and server-side cache.

### Zustand Stores (Local State)
Lightweight Zustand stores handle fast, synchronous client-side state:
* **`authStore`:** Manages active JWT tokens, authentication status (`isAuthenticated`), and user profile details (`user`).
* **`uiStore`:** Manages global UI elements (e.g., side drawers, mobile menu visibility, notifications/toasts queue).
* **`cartStore`:** Manages local cart operations before checkout synchronization.

### TanStack Query (Server Cache)
Used to manage all asynchronous remote API requests.
* **Query Key Strategy:** Keys are designed as hierarchical arrays:
  * Categories: `['categories', 'tree']`
  * Products: `['products', 'list', filters]`, `['products', 'detail', slug]`
* **Retry Engine Configuration:**
  * Enabled (up to 3 retries) for 5xx errors to handle transient network issues.
  * Disabled for 4xx errors (401, 403, 404, 409) since retrying client-side parameter failures is redundant.
* **Cache Expiry:** Default `staleTime` is set to 5 minutes for stable lists (e.g. category trees) to prevent unnecessary network overhead.

---

## 5. UI Theme System & Design Standards

Visual styles align with the modern design guidelines of the EGO platform:
* **Typography:** Integrates custom Google Fonts (`Outfit` for primary headers, `Inter` for clean body content).
* **Color System:** tailors Harmonious HSL color palettes. Custom gradients and dark styles match streetwear brand aesthetics.
* **Surfaces:** Uses curved design layouts (e.g., `borderRadius: '12px'`) and clean card interfaces.
* **Global Notifications:** Toast messages triggered via `toast.error()` or `toast.success()` are resolved inside a global `GlobalToastRenderer` which renders MUI Snackbars dynamically.
