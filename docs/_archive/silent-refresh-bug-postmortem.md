# Bug Post-Mortem: Silent Refresh Failure Chain

> **Status:** Resolved — 2026-05-22  
> **Severity:** Critical (users silently logged out on token expiry, auth state destroyed on server errors)  
> **Affected:** Frontend (`client.ts`, `main.tsx`) + Backend (`SecurityConfig.java`)

---

## Summary

After implementing the Phase 1 frontend authentication layer, three interdependent bugs were
discovered that caused the silent AT-refresh mechanism to completely fail:

1. **Axios interceptor caught 401s from `/auth/login`** — causing a forced page reload on wrong-password login attempts, making the form appear broken.
2. **Backend returned 500 (NPE) instead of 401** when an expired AT was sent to `GET /auth/me` — because `/auth/me` was incorrectly listed as a public endpoint in `SecurityConfig`.
3. **`bootRefresh()` in `main.tsx` used a single try/catch** — a 500 from `/auth/me` would silently destroy the user's valid refresh token by calling `clearAuth()`.

These three bugs formed a chain: the 500 bypassed the interceptor's refresh logic (which only triggers on 401), which then hit the single-catch block in `bootRefresh`, which wiped auth state completely.

---

## Detailed Root Cause Analysis

### Bug 1 — Interceptor Caught 401s from Login/Register Endpoints

**File:** `src/api/client.ts`

**What happened:**

The Axios response interceptor was designed to catch 401s and silently refresh the AT.
However, the original exclusion list only blocked the `/auth/refresh` endpoint itself:

```ts
// BEFORE (buggy)
if (
  error.response?.status === 401 &&
  !originalRequest._retry &&
  !originalRequest.url?.includes('/auth/refresh')  // only this one excluded
) {
```

When a user typed the wrong password:
1. `POST /auth/login` → backend returned 401 (bad credentials)
2. Interceptor saw 401 → no RT in localStorage → called `clearAuth()` + `window.location.href = '/login'`
3. Page did a **hard reload** to `/login`
4. User saw the login form re-appear with no error message — form appeared "broken"

**Fix:**

```ts
// AFTER (fixed)
if (
  error.response?.status === 401 &&
  !originalRequest._retry &&
  !originalRequest.url?.includes('/auth/refresh') &&
  !originalRequest.url?.includes('/auth/login') &&     // ← added
  !originalRequest.url?.includes('/auth/register')     // ← added
) {
```

**Rule established:** Any endpoint that legitimately returns 401 as part of its normal
error response (wrong credentials, unauthenticated access) must be excluded from the
silent-refresh trigger.

---

### Bug 2 — Backend Returned 500 (NPE) Instead of 401 on Expired AT

**File:** `raw-ego/src/main/java/com/ego/raw_ego/auth/security/SecurityConfig.java`

**What happened:**

`SecurityConfig.PUBLIC_MATCHERS` contained the wildcard `/api/v1/auth/**`:

```java
// BEFORE (buggy)
private static final String[] PUBLIC_MATCHERS = {
    "/api/v1/auth/**",   // ← This wildcard matched /auth/me!
    ...
};
```

Spring Security's filter chain applies to all requests. For **protected** routes, if the
JWT is expired, `JwtAuthenticationFilter` fails validation, does NOT populate the
`SecurityContext`, and the `JwtAuthenticationEntryPoint` fires → returns 401.

For **public** routes (like `/api/v1/auth/**`), the filter still runs but on failure the
request is **passed through** — the endpoint is not blocked. This means:

```
Expired AT sent to GET /api/v1/auth/me
│
├── JwtAuthenticationFilter: JWT expired → validation fails
│   └── SecurityContext NOT populated (user = null)
│
├── Route is PUBLIC → request passes through (no 401 from filter)
│
└── AuthController.getCurrentUser(@AuthenticationPrincipal UserDetails userDetails)
      └── userDetails is NULL (SecurityContext empty)
      └── userDetails.getUsername()  ← NullPointerException!
      └── GlobalExceptionHandler catches Exception → 500 Internal Server Error
```

**Observed symptoms:**
- Network tab showed: `GET /auth/me → 500` (not 401)
- Console: `GET http://localhost:8080/api/v1/auth/me 500 (Internal Server Error)` × 4
  (1 initial attempt + 2 TanStack Query retries for 5xx + 1 from test panel)
- Axios interceptor's refresh logic **never triggered** (only handles 401, not 500)

**Fix:**

```java
// AFTER (fixed) — explicit list, no wildcard
private static final String[] PUBLIC_MATCHERS = {
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    // /api/v1/auth/me is intentionally ABSENT — it falls through to anyRequest().authenticated()
    "/docs", "/docs/**", "/v3/api-docs", "/v3/api-docs/**",
    "/swagger-ui/**", "/swagger-ui.html", "/actuator/health"
};
```

`GET /auth/me` now goes through `anyRequest().authenticated()`. When the JWT is expired:
- `JwtAuthenticationFilter` fails validation
- `JwtAuthenticationEntryPoint` fires → returns **401** with proper JSON body
- Axios interceptor catches the 401 → silently refreshes → retries → ✅

**Rule established:** Never use `/**` wildcards in PUBLIC_MATCHERS for an entire
controller. List only the specific endpoints that truly require no authentication.

---

### Bug 3 — Single try/catch in `bootRefresh()` Destroyed Valid Tokens on Server Errors

**File:** `raw-ego-frontend/src/main.tsx`

**What happened:**

The `bootRefresh()` function wrapped both the token refresh call AND the `/auth/me` call
in a single try/catch. Any exception from either call fell into the same catch block,
which called `clearAuth()`:

```ts
// BEFORE (buggy)
const bootRefresh = async () => {
  try {
    // Step 1: POST /auth/refresh → store tokens ✅
    const refreshRes = await axios.post('/auth/refresh', ...);
    useAuthStore.getState().setTokens(accessToken, refreshToken);

    // Step 2: GET /auth/me → store user
    const meRes = await axios.get('/auth/me', ...);
    useAuthStore.getState().setUser(meRes.data.data);

  } catch {
    // ← This catches BOTH refresh failures AND /auth/me failures
    useAuthStore.getState().clearAuth();   // ← DESTROYS VALID TOKENS!
  }
};
```

**The failure chain with Bug 2 active:**

```
Page refresh → bootRefresh() runs
│
├── POST /auth/refresh → 200 ✅ → tokens stored (AT valid, RT valid)
│
├── GET /auth/me → 500 NPE (Bug 2)
│
└── catch block → clearAuth()
      ├── localStorage.removeItem('ego_rt')  ← RT DELETED
      ├── accessToken = null                 ← AT CLEARED
      └── isAuthenticated = false            ← USER LOGGED OUT
```

Result: User had perfectly valid tokens but was silently logged out because the backend's
`/auth/me` endpoint threw a 500 due to Bug 2.

**Fix:**

```ts
// AFTER (fixed) — two independent try/catch blocks
const bootRefresh = async () => {
  // Step 1: Refresh tokens — clearAuth ONLY on refresh failure (invalid RT)
  let accessToken: string;
  try {
    const refreshRes = await axios.post('/auth/refresh', ...);
    accessToken = refreshRes.data.data.accessToken;
    useAuthStore.getState().setTokens(accessToken, refreshRes.data.data.refreshToken);
  } catch {
    useAuthStore.getState().clearAuth();  // ← ONLY here: RT is invalid
    return;
  }

  // Step 2: Fetch user profile — NEVER clearAuth on server error
  try {
    const meRes = await axios.get('/auth/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    useAuthStore.getState().setUser(meRes.data.data);
  } catch {
    // 500 / network error: tokens remain valid, user fetched lazily later
    console.warn('[boot] /auth/me failed — user profile will load lazily');
  }
};
```

**Rule established:** Never put both token lifecycle operations and user-data fetches in
the same try/catch. Clearing auth should only happen when the RT itself is invalid —
never as a side effect of a server error on a data endpoint.

---

## Additional Bugs Fixed in the Same Session

### Bug 4 — Missing `ToastRenderer` Component

`uiStore.ts` had `toast.error()` / `toast.success()` helpers that pushed to Zustand state,
but no component ever read from `uiStore.toasts` and rendered them. Every `toast.error()`
call was silently discarded.

**Fix:** Created `src/components/ui/GlobalToastRenderer.tsx` (MUI Snackbar stack) and
mounted it inside `AppProviders.tsx`.

---

### Bug 5 — Login Error Not Surfaced to Form

`useLogin`'s `onError` handler called `toast.error(message)` for generic auth errors
(e.g. 401 bad credentials). Since Bug 4 meant toasts were invisible, users saw no
feedback on wrong password.

**Fix:** When `onFieldError` callback is registered (i.e. a form is listening), route
non-field errors through `onFieldError('root', message)` so the form's `<Alert>` component
displays inline. `toast.error` is kept as fallback for non-form contexts.

---

### Bug 6 — Axios Type Mismatch (`AxiosRequestConfig` vs `InternalAxiosRequestConfig`)

The interceptor cast `error.config` to `AxiosRequestConfig` (Axios input type) where
`headers` is `optional`. In Axios 1.x, `error.config` is actually `InternalAxiosRequestConfig`
where `headers: AxiosHeaders` is **always defined**. This caused the `if (originalRequest.headers)`
guard to potentially evaluate `false`, silently skipping the `Authorization` header
update before retry.

**Fix:**
```ts
// BEFORE
import { type AxiosRequestConfig } from 'axios';
const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };
if (originalRequest.headers) {
  originalRequest.headers['Authorization'] = `Bearer ${newAt}`;   // might be skipped
}

// AFTER
import { type InternalAxiosRequestConfig } from 'axios';
type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean };
const originalRequest = error.config as RetryableConfig;
// headers.delete() before retry — let request interceptor re-attach fresh token
originalRequest.headers.delete('Authorization');
return apiClient(originalRequest);
```

---

### Bug 7 — Logout Race Condition (ProtectedRoute Adding `?redirect`)

**Sequence (buggy):**
1. User on `/account` clicks Logout
2. `useLogout.onSettled`: `clearAuth()` → `isAuthenticated = false`
3. React re-renders → `ProtectedRoute` sees `isAuthenticated = false` on `/account`
4. `ProtectedRoute` fires: `<Navigate to="/login?redirect=%2Faccount" />`
5. `useLogout.onSettled` then runs `navigate('/login')` (already there)
6. URL = `/login?redirect=%2Faccount` — redirect param from the ProtectedRoute race
7. Next login → `useLogin` reads `?redirect` → user lands on `/account` instead of home

**Fix:** Changed execution order and logout destination:
```ts
// BEFORE
clearAuth();      // ← triggers ProtectedRoute race
qc.clear();
navigate('/login');

// AFTER
qc.clear();       // ← clear cache first (no stale refetches)
clearAuth();      // ← isAuthenticated becomes false
navigate('/');    // ← home, not /login; ProtectedRoute has nothing to race against
```

---

## Verified Working Flow (Post-Fix)

The following network sequence was confirmed with `spring.jwt.access-token-expiry-ms=10000`:

```
1. POST /api/v1/auth/login          → 200   AT issued (TTL: 10s)

[... 10 seconds pass ...]

2. GET  /api/v1/auth/me             → 401   AT expired, proper 401 from Spring Security
   [apiClient] 401 on [GET /auth/me] — attempting silent refresh

3. POST /api/v1/auth/refresh        → 200   New AT + RT issued, old RT rotated
   [apiClient] Calling /auth/refresh...
   [apiClient] Token refresh successful — new AT received

4. GET  /api/v1/auth/me (retry)     → 200   Original request replayed with new AT
   ✅ User profile returned, UI shows correct data — no redirect, no flash
```

---

## Files Changed

| File | Type | Change |
|------|------|--------|
| `raw-ego/.../SecurityConfig.java` | Backend | Replaced `/api/v1/auth/**` wildcard with explicit endpoint list; `/auth/me` now protected |
| `raw-ego-frontend/src/api/client.ts` | Frontend | Added `/auth/login` + `/auth/register` to interceptor exclusion list; fixed `InternalAxiosRequestConfig` type; used `headers.delete()` before retry |
| `raw-ego-frontend/src/main.tsx` | Frontend | Split single try/catch into two independent blocks |
| `raw-ego-frontend/src/hooks/useAuth.ts` | Frontend | Fixed `onError` to route auth errors to form; fixed logout order + destination |
| `raw-ego-frontend/src/types/api.types.ts` | Frontend | Fixed `ApiErrorResponse.errors` type from `Record<string, string>` to `Array<{field, message}>` to match backend `List<ApiError>` |
| `raw-ego-frontend/src/utils/apiError.ts` | Frontend | Updated `extractApiError` to map backend array format to `Record<string, string>` |
| `raw-ego-frontend/src/components/ui/GlobalToastRenderer.tsx` | Frontend | Created — renders `uiStore.toasts` as MUI Snackbar stack |
| `raw-ego-frontend/src/providers/AppProviders.tsx` | Frontend | Mounted `GlobalToastRenderer` |

---

## Rules / Architectural Decisions Established

1. **Interceptor exclusion list** — All endpoints that return legitimate 401s (login, register, refresh) must be explicitly excluded from the silent-refresh trigger.

2. **`PUBLIC_MATCHERS` — no wildcards on auth controllers** — List only truly unauthenticated endpoints by name. `/auth/me`, `/auth/logout` require a valid Bearer token.

3. **`bootRefresh` — two try/catch blocks** — Token refresh failure = clear auth. Any other failure = warn + continue. A server error on a data endpoint must never destroy valid auth tokens.

4. **`InternalAxiosRequestConfig` in interceptors** — Always use the internal Axios 1.x type in response interceptors. `error.config` is `InternalAxiosRequestConfig`, not `AxiosRequestConfig`. `headers` is always an `AxiosHeaders` instance — use `.set()` / `.delete()` not bracket notation.

5. **Logout order: `qc.clear()` → `clearAuth()` → `navigate('/')`** — Clearing the query cache before setting `isAuthenticated = false` prevents TanStack Query from firing stale refetches. Navigating to home (not `/login`) prevents `ProtectedRoute` from injecting `?redirect` params.

6. **Backend error types match frontend expectations** — Backend `List<ApiError>` (array of objects) must be mapped client-side; do not assume a `Record<string, string>` map format.
