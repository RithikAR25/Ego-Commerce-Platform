# Authentication Flow — EGO Platform Frontend

> Mirrors the backend's stateless JWT + refresh token rotation architecture exactly.  
> See also: `/docs/security/jwt-flow.md`, `/docs/security/refresh-token-architecture.md`

---

## Token Storage

| Token | Storage | Why |
|---|---|---|
| **Access Token (AT)** | Zustand `authStore.accessToken` (in-memory) | XSS cannot read memory; clears on tab close |
| **Refresh Token (RT)** | `localStorage` key `ego_rt` | Survives page refresh; RT alone cannot authenticate |

---

## App Boot Sequence (`main.tsx` / `AppProviders.tsx`)

```
App loads
  │
  ├── Check localStorage for 'ego_rt'
  │
  ├── If found:
  │     └── POST /api/v1/auth/refresh { refreshToken: ego_rt }
  │           ├── Success → setTokens(at, newRt) → user stays logged in
  │           └── Failure → clearAuth() → user sees public app (not kicked to login)
  │
  └── If not found: user is a guest
```

This is a **silent boot refresh** — the user never sees a flash of logged-out state.

---

## Register Flow

```
1. User fills RegisterForm (React Hook Form + Zod validation)
2. On submit → POST /api/v1/auth/register
3. Backend returns 201 { data: { accessToken, refreshToken, user } }
4. authStore.setTokens(at, rt) — stores AT in memory, RT in localStorage
5. authStore.setUser(user)
6. Navigate to / (or back to redirect param)
7. TanStack Query's 'auth/me' cache is populated
```

---

## Login Flow

```
1. User fills LoginForm
2. On submit → POST /api/v1/auth/login { email, password }
3. Backend returns 200 { data: { accessToken, refreshToken, user } }
4. Same as steps 4-7 above
```

---

## Silent Token Refresh (Interceptor — the critical piece)

```
Any API call → 401 Unauthorized
  │
  ├── Is a refresh already in progress?
  │     YES → queue this request (do not trigger another refresh)
  │     NO  → start refresh
  │
  │── POST /api/v1/auth/refresh { refreshToken: localStorage.ego_rt }
  │     │
  │     ├── Success (200):
  │     │     - Store new AT in authStore (in-memory)
  │     │     - Store new RT in localStorage
  │     │     - Drain the queue: retry all queued requests with new AT
  │     │     - Retry the original failed request
  │     │
  │     └── Failure (401):
  │           - clearAuth() — wipe AT from memory, RT from localStorage
  │           - Drain queue with rejection
  │           - Redirect to /login
  │           (This triggers when the RT itself is expired or revoked)
```

**The queue pattern** ensures that if 5 API calls fire simultaneously when the AT expires, only ONE refresh request is sent — the other 4 wait and then retry with the new token.

---

## Logout Flow

```
1. User clicks Logout
2. POST /api/v1/auth/logout { refreshToken: localStorage.ego_rt }
   (Backend: revokes the specific RT in DB → token family invalidated)
3. authStore.clearAuth() — clears AT from memory, RT from localStorage
4. queryClient.clear() — wipes all TanStack Query cache
5. Navigate to /login or /
```

---

## Token Theft Detection (Backend-Driven)

When the backend detects **refresh token reuse** (the same RT used twice — classic theft scenario):
- Backend revokes the **entire token family**
- Returns 401 on the refresh call
- Frontend interceptor calls `clearAuth()` and redirects to `/login`
- User sees: "Session expired, please log in again"

The frontend has no special handling for this — it just handles the 401 from the refresh endpoint like any other refresh failure.

---

## RBAC on the Frontend

| Check | Implementation | When |
|---|---|---|
| Route guard | `AdminRoute.tsx` checks `user.role === 'ADMIN'` | On route render |
| Conditional UI | `user?.role === 'ADMIN'` in components | Show/hide admin buttons |
| API protection | Backend enforces `hasRole('ADMIN')` on `/admin/**` | Real enforcement |

**The frontend role check is UX only.** The backend is the source of truth. A CUSTOMER who manually navigates to `/admin` gets redirected by `AdminRoute` — and even if they somehow bypass it, every `/admin/**` API call returns 403.

---

## `passwordChangedAt` Guard (Backend-Enforced)

When a user changes their password, the backend sets `passwordChangedAt` on their account. Any AT issued BEFORE that timestamp is rejected by `JwtAuthenticationFilter`.

**Frontend behavior:** The API returns 401 → interceptor tries refresh → if the RT is also expired, clears auth → user logs in again. The frontend does not know about `passwordChangedAt` — it just handles the resulting 401.
