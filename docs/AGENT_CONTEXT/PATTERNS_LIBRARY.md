# EGO Patterns Library

This file catalogues every reusable engineering pattern implemented in the EGO codebase.

**How to use this file:**
- **Working on EGO?** Each row links directly to the EGO source file where the pattern lives.
- **Starting a new project?** Each row also links to the general standard in `dev-standards` — a portable, EGO-independent description of the same pattern.
- **Agents:** Read this file to understand which patterns in EGO are intentional, reusable designs — not project-specific choices.

**General standards repo:** [github.com/RithikAR25/dev-standards](https://github.com/RithikAR25/dev-standards)

---

## Backend Patterns

### API & Response Layer

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| `ApiResponse<T>` envelope (success + error factories) | `raw-ego/.../common/response/ApiResponse.java` | ✅ Any Spring REST API | [api-design.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/api-design.md) |
| `ApiError` (field + message validation error shape) | `raw-ego/.../common/response/ApiError.java` | ✅ Any Spring REST API | [api-design.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/api-design.md) |
| `GlobalExceptionHandler` (`@RestControllerAdvice`) | `raw-ego/.../common/exception/GlobalExceptionHandler.java` | ✅ Any Spring REST API | [api-design.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/api-design.md) |
| `EgoException` base class (carries `HttpStatus`) | `raw-ego/.../common/exception/EgoException.java` | ✅ Any Spring REST API | [spring-boot-standards.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/spring-boot-standards.md) |
| Exception hierarchy (Auth, NotFound, Conflict, etc.) | `raw-ego/.../common/exception/` | ✅ Any Spring REST API | [spring-boot-standards.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/spring-boot-standards.md) |
| Slug-based public routes (not DB primary keys) | `raw-ego/.../catalog/controller/ProductController.java` | ✅ Any public-facing storefront | [api-design.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/api-design.md) |

---

### Security Patterns

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| Stateless JWT security config (`STATELESS` session policy) | `raw-ego/.../auth/security/SecurityConfig.java` | ✅ Any Spring stateless API | [security.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/security.md) |
| JWT sign + verify + claims extraction | `raw-ego/.../auth/security/JwtService.java` | ✅ Any Spring JWT app | [security.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/security.md) |
| JWT filter (extracts token, password-changed guard) | `raw-ego/.../auth/security/JwtAuthenticationFilter.java` | ✅ Any Spring JWT app | [security.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/security.md) |
| Token rotation (old RT deleted on every refresh) | `raw-ego/.../auth/service/RefreshTokenService.java` | ✅ Any Spring JWT app | [security.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/security.md) |
| Token family revocation (theft detection via `family_id`) | `raw-ego/.../auth/service/RefreshTokenService.java` | ✅ Any Spring JWT app | [security.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/security.md) |
| SHA-256 refresh token hashing (raw UUID in transit only) | `raw-ego/.../auth/entity/RefreshToken.java` | ✅ Any Spring JWT app | [security.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/security.md) |
| HMAC-SHA256 webhook verification with explicit UTF-8 | `raw-ego/.../payment/service/WebhookVerificationService.java` | ✅ Any webhook receiver | [security.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/security.md) |
| Raw body webhook reading (`getInputStream().readAllBytes()`) | `raw-ego/.../payment/controller/WebhookController.java` | ✅ Any webhook receiver | [security.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/security.md) |

---

### Persistence Patterns

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| `@Modifying(clearAutomatically = true)` on bulk UPDATE | `raw-ego/.../cart/repository/InventoryRecordRepository.java` | ✅ Any JPA bulk update | [persistence.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/persistence.md) |
| `saveAndFlush()` for same-transaction read-back | `raw-ego/.../catalog/service/ProductService.java` | ✅ Any JPA service | [persistence.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/persistence.md) |
| Bidirectional relationship wiring before `save()` | `raw-ego/.../catalog/service/ProductService.java` | ✅ Any JPA bidirectional entity | [persistence.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/persistence.md) |
| `@Version` optimistic locking on concurrent entities | `raw-ego/.../cart/entity/InventoryRecord.java` | ✅ Any concurrent entity | [persistence.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/persistence.md) |
| Optimistic lock retry (max 3 → `409 Conflict`) | `raw-ego/.../cart/service/InventoryReservationService.java` | ✅ Any optimistic lock scenario | [persistence.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/persistence.md) |
| Price + data snapshot on order items (immutable at checkout) | `raw-ego/.../order/entity/OrderItem.java` | ✅ Any e-commerce or billing system | [persistence.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/persistence.md) |
| External HTTP calls OUTSIDE `@Transactional` boundary | `raw-ego/.../catalog/service/ProductImageService.java` | ✅ Any service with external calls | [persistence.md](https://github.com/RithikAR25/dev-standards/blob/main/backend/persistence.md) |

---

### Concurrency Patterns

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| Redis `SETNX` distributed lock (lock + bounded TTL + unique value) | `raw-ego/.../cart/service/CheckoutService.java` | ✅ Any concurrent critical section | [concurrency.md](https://github.com/RithikAR25/dev-standards/blob/main/architecture/concurrency.md) |
| Redis lock key naming convention (`lock:{type}:{id}`) | `raw-ego/.../cart/service/CheckoutService.java` | ✅ Any Redis lock scenario | [concurrency.md](https://github.com/RithikAR25/dev-standards/blob/main/architecture/concurrency.md) |

---

### Async & Event-Driven Patterns

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| `ApplicationEvent` publish (decouple side effects) | `raw-ego/.../order/service/OrderService.java` | ✅ Any event-driven notification | [event-driven.md](https://github.com/RithikAR25/dev-standards/blob/main/architecture/event-driven.md) |
| `@Async @EventListener` (non-blocking side effect handler) | `raw-ego/.../notification/` | ✅ Any async notification | [event-driven.md](https://github.com/RithikAR25/dev-standards/blob/main/architecture/event-driven.md) |
| `@TransactionalEventListener(AFTER_COMMIT)` | `raw-ego/.../notification/` | ✅ Any side effect that must not fire on rollback | [event-driven.md](https://github.com/RithikAR25/dev-standards/blob/main/architecture/event-driven.md) |
| Transactional Outbox (outbox record in same TX as business write) | `raw-ego/.../search/entity/SearchOutboxRecord.java` | ✅ Any durable event delivery | [event-driven.md](https://github.com/RithikAR25/dev-standards/blob/main/architecture/event-driven.md) |
| `@Scheduled` outbox poller (per-record try/catch) | `raw-ego/.../search/service/SearchSyncService.java` | ✅ Any durable event delivery | [event-driven.md](https://github.com/RithikAR25/dev-standards/blob/main/architecture/event-driven.md) |

---

## Frontend Patterns

### HTTP & Auth Patterns

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| Queued silent refresh Axios interceptor (`isRefreshing` + `failedQueue`) | `raw-ego-frontend/src/api/client.ts` | ✅ Any React JWT app | [ADR-003](https://github.com/RithikAR25/dev-standards/blob/main/decisions/ADR-003-jwt-rotation-strategy.md) |
| Raw axios (not `apiClient`) for the refresh call itself (avoids recursion) | `raw-ego-frontend/src/api/client.ts` | ✅ Any React JWT app | [ADR-003](https://github.com/RithikAR25/dev-standards/blob/main/decisions/ADR-003-jwt-rotation-strategy.md) |
| 401 → silent refresh → replay original request | `raw-ego-frontend/src/api/client.ts` | ✅ Any React JWT app | [react-typescript.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/react-typescript.md) |
| 403 → redirect to `/403` in interceptor | `raw-ego-frontend/src/api/client.ts` | ✅ Any RBAC React app | [react-typescript.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/react-typescript.md) |
| AT in-memory (Zustand), RT in localStorage | `raw-ego-frontend/src/store/authStore.ts` | ✅ Any React JWT app | [zustand.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/zustand.md) |
| Logout sequence: `qc.clear()` → `clearAuth()` → `navigate('/')` | `raw-ego-frontend/src/features/auth/hooks/useAuth.ts` | ✅ Any React JWT app | [react-typescript.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/react-typescript.md) |

---

### State Management Patterns

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| Zustand `authStore` (AT in-memory, RT in localStorage, `isAuthenticated`) | `raw-ego-frontend/src/store/authStore.ts` | ✅ Any React auth app | [zustand.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/zustand.md) |
| Zustand `uiStore` with `getState()` toast helpers (works outside React) | `raw-ego-frontend/src/store/uiStore.ts` | ✅ Any React app with toasts | [zustand.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/zustand.md) |
| `useAuthStore.getState()` in Axios interceptor (outside React tree) | `raw-ego-frontend/src/api/client.ts` | ✅ Any Zustand + Axios app | [zustand.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/zustand.md) |
| TanStack Query key factory (hierarchical arrays) | `raw-ego-frontend/src/api/*.api.ts` | ✅ Any React + TanStack Query app | [tanstack-query.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/tanstack-query.md) |
| Retry: enabled for 5xx, disabled for 4xx | `raw-ego-frontend/src/providers/AppProviders.tsx` | ✅ Any TanStack Query app | [tanstack-query.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/tanstack-query.md) |
| `setQueryData` for instant optimistic UI update | `raw-ego-frontend/src/features/cart/hooks/useCart.ts` | ✅ Any TanStack Query mutation | [tanstack-query.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/tanstack-query.md) |

---

### Routing Patterns

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| `ProtectedRoute` (synchronous Zustand `isAuthenticated` check) | `raw-ego-frontend/src/router/ProtectedRoute.tsx` | ✅ Any React auth app | [frontend-architecture.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/frontend-architecture.md) |
| `AdminRoute` (role check from Zustand `user.role`) | `raw-ego-frontend/src/router/AdminRoute.tsx` | ✅ Any RBAC React app | [frontend-architecture.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/frontend-architecture.md) |
| Admin pages lazy-loaded (`React.lazy()` + `<Suspense>`) | `raw-ego-frontend/src/router/index.tsx` | ✅ Any React app with admin | [frontend-architecture.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/frontend-architecture.md) |

---

### Design System Patterns

| Pattern | EGO Source File | Reusable? | dev-standards Reference |
|---|---|---|---|
| MUI palette custom token namespaces (`surface`, `border`, `brand`, `state`, `overlay`, `statusColors`) | `raw-ego-frontend/src/theme/palette.ts` | ✅ Any React + MUI app | [mui-design-system.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/mui-design-system.md) |
| Full semantic typography scale (10 named variants) | `raw-ego-frontend/src/theme/typography.ts` | ✅ Any React + MUI app | [mui-design-system.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/mui-design-system.md) |
| MUI module augmentation for custom theme tokens | `raw-ego-frontend/src/theme/palette.ts` + `typography.ts` | ✅ Any React + MUI app | [mui-design-system.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/mui-design-system.md) |
| MUI component overrides reading from `baseTheme.*` | `raw-ego-frontend/src/theme/theme.ts` | ✅ Any React + MUI app | [mui-design-system.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/mui-design-system.md) |
| `statusColors` map (backend enum → display color) | `raw-ego-frontend/src/theme/palette.ts` | ✅ Any app with order/status enums | [mui-design-system.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/mui-design-system.md) |
| Dark/light mode via `PaletteMode` parameter in `getTheme(mode)` | `raw-ego-frontend/src/theme/theme.ts` | ✅ Any React + MUI app | [mui-design-system.md](https://github.com/RithikAR25/dev-standards/blob/main/frontend/mui-design-system.md) |

---

## EGO-Specific Patterns (Not Directly Reusable)

These patterns are tightly coupled to EGO's business domain and are not designed for extraction — but they are documented here for full awareness.

| Pattern | EGO Source | Why EGO-Specific |
|---|---|---|
| Flat 2-level category structure (locked decision) | `catalog/entity/Category.java` | Deliberate business constraint for this storefront |
| SKU format: `{BASE_CODE}-{COLOR}-{SIZE}` | `catalog/service/VariantService.java` | EGO product catalog convention |
| 3-tier decimal pricing (`cost_price`, `selling_price`, `mrp`) | `catalog/entity/ProductVariant.java` | EGO's specific pricing model |
| Cart backed by Redis session (not DB) | `cart/` module | EGO's session cart architecture |
| Return window (7 days from delivery) | `returns/service/ReturnService.java` | EGO business policy |

---

## Pattern Adoption Status

| Pattern | Status in EGO | Notes |
|---|---|---|
| Transactional outbox | ✅ Implemented (Search sync) | Not yet applied to notification module |
| `spacing.ts` / `shadows.ts` split | ⚠️ Not yet split | Currently inline in `theme.ts` — future projects should split from day one |
| `components.ts` override split | ⚠️ Not yet split | Currently inline in `theme.ts` |
| Full error boundaries (per-page) | ⚠️ Partial | Top-level boundary exists; per-page boundaries not yet added |
| Zod schema validation (frontend) | 🔲 Not yet added | Jakarta validation exists on backend; Zod not yet mirroring it on frontend |
