# architecture-context.md — EGO Platform

> **Purpose:** Technical architecture for AI agents — backend lifecycle, frontend structure, state model, auth flow, and data flows.  
> **Source-verified:** June 6, 2026.

---

## 1. Backend Architecture

### Module Structure

The backend is a Spring Boot 4 monolith with 14 feature modules, all under `com.ego.raw_ego`:

| Package | Responsibility |
|---|---|
| `auth` | JWT auth, refresh token rotation, user registration/login |
| `catalog` | Products, categories, variants, attributes, images |
| `cart` | Redis cart, anonymous cart, inventory reservation |
| `order` | Checkout pipeline, order lifecycle, status history |
| `payment` | Razorpay order creation, webhook, HMAC verification |
| `returns` | Return requests, Razorpay refunds |
| `review` | Purchase-gated product reviews |
| `wishlist` | Variant-level wishlist (MySQL-backed) |
| `coupon` | Coupon codes, discount computation at checkout |
| `notification` | Async SendGrid emails via Spring Events |
| `search` | Elasticsearch outbox, indexing, faceted search |
| `address` | User address book |
| `admin` | Dashboard KPI metrics |
| `common` | `ApiResponse<T>`, exceptions, `HtmlSanitizer`, `SlugUtils` |

### Request Lifecycle

```
HTTP Request
    │
    ▼
CorsFilter
    │
    ▼
JwtAuthenticationFilter (OncePerRequestFilter)
    ├── Read Authorization: Bearer <token>
    ├── Validate signature + expiry (JJWT 0.12.3)
    ├── Load UserDetails from DB
    ├── Check passwordChangedAt > token.issuedAt → reject
    └── Set SecurityContextHolder
    │
    ▼
AuthorizationFilter (Spring Security)
    ├── PUBLIC_MATCHERS → permitAll()
    ├── /admin/**      → ROLE_ADMIN
    └── anyRequest     → authenticated()
    │
    ▼
Controller → Service → Repository → DB
    │
    ▼
ApiResponse<T> envelope:
{
  "success": true,
  "message": "...",
  "data": { ... },
  "errors": null,
  "timestamp": "2026-06-06T..."
}
```

### Standard API Response Envelope

All endpoints return `ApiResponse<T>` from `common.response`:
- `success: boolean`
- `message: String`
- `data: T` (null on error)
- `errors: ApiError[]` (null on success, populated on 400)
- `timestamp: Instant`

### Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps all exceptions:
- `EgoException` subclasses → their embedded `HttpStatus`
- `@Valid` failures → `400` with `errors[]` array
- Uncaught → `500` (no stack trace in response)

### Key Architecture Rules (from `AGENT_CONTEXT/ARCHITECTURE_RULES.md`)

1. **No Cloudinary/Razorpay calls inside `@Transactional`** — external HTTP calls never hold DB connections
2. **`saveAndFlush()` not `save()`** — required when DTO is built from the same entity in the same transaction
3. **`@Modifying(clearAutomatically = true)`** — required on all bulk JPQL UPDATE queries to prevent 1st-level cache staleness
4. **No cross-module entity imports** — modules communicate via service methods, not entity references
5. **HtmlSanitizer** on all free-text user input at service layer write time

---

## 2. Frontend Architecture

### Folder Structure

```
src/
├── api/           ← Axios API functions (one file per domain: auth.api.ts, cart.api.ts, ...)
├── components/    ← Shared reusable components
│   ├── layout/    ← RootLayout, CheckoutLayout, AdminLayout
│   └── ui/        ← PageLoader, PageTransitionLoader, etc.
├── features/      ← Feature-sliced: each feature = self-contained module
│   ├── auth/      ← pages/, hooks/, components/
│   ├── catalog/   ← storefront/ + admin/ sub-splits
│   ├── checkout/
│   ├── orders/    ← storefront/ + admin/ sub-splits
│   └── ...
├── hooks/         ← Global custom hooks
├── pages/         ← Top-level simple pages (HomePage, AccountPage, NotFoundPage)
├── providers/     ← AppProviders.tsx (wraps app in QueryClient + ThemeProvider)
├── router/        ← createBrowserRouter config, ProtectedRoute, AdminRoute
├── schemas/       ← Zod schemas for form validation
├── store/         ← Zustand stores (authStore.ts, cartStore.ts)
├── theme/         ← MUI theme override (dark mode, square corners, custom palette)
├── types/         ← TypeScript interfaces by domain (*.types.ts)
└── utils/         ← cloudinary URL builders, formatters
```

### Routing Structure

React Router v7 `createBrowserRouter`. Three layout regions:

| Layout | Component | Path | Guard |
|---|---|---|---|
| Storefront | `RootLayout` (Navbar + Footer) | `/`, `/products`, `/orders`, etc. | None / ProtectedRoute |
| Checkout | `CheckoutLayout` (logo only) | `/checkout/**` | ProtectedRoute |
| Admin | `AdminLayout` (sidebar) | `/admin/**` | AdminRoute (ROLE_ADMIN) |

All pages are lazy-loaded via `React.lazy()` with `<Suspense fallback={<PageLoader />}>`.

### Route Map (source-verified)

| Path | Component | Guard |
|---|---|---|
| `/` | `HomePage` | None |
| `/login` | `LoginPage` | None |
| `/register` | `RegisterPage` | None |
| `/auth/verify-email` | `VerifyEmailPage` | None |
| `/auth/forgot-password` | `ForgotPasswordPage` | None |
| `/auth/reset-password` | `ResetPasswordPage` | None |
| `/products` | `ProductListingPage` | None |
| `/products/:slug` | `ProductDetailPage` | None |
| `/cart` | `CartPage` | ProtectedRoute |
| `/wishlist` | `WishlistPage` | ProtectedRoute |
| `/account` | `AccountPage` | ProtectedRoute |
| `/orders` | `CustomerOrdersPage` | ProtectedRoute |
| `/orders/:orderId` | `CustomerOrderDetailPage` | ProtectedRoute |
| `/checkout` | `CheckoutPage` | ProtectedRoute |
| `/checkout/verify/:orderId` | `PaymentVerificationPage` | ProtectedRoute |
| `/checkout/success/:orderId` | `OrderSuccessPage` | ProtectedRoute |
| `/admin` | `AdminDashboardPage` | AdminRoute |
| `/admin/categories` | `AdminCategoriesPage` | AdminRoute |
| `/admin/products` | `AdminProductsPage` | AdminRoute |
| `/admin/products/:id` | `AdminProductDetailPage` | AdminRoute |
| `/admin/orders` | `AdminOrdersPage` | AdminRoute |
| `/admin/orders/:id` | `AdminOrderDetailPage` | AdminRoute |
| `/admin/returns` | `AdminReturnsPage` | AdminRoute |
| `/admin/returns/:id` | `AdminReturnDetailPage` | AdminRoute |
| `/admin/inventory` | `AdminInventoryPage` | AdminRoute |
| `/admin/coupons` | `AdminCouponsPage` | AdminRoute |
| `/admin/users` | `AdminUsersPage` | AdminRoute |

### State Management

| State Type | Tool | Scope |
|---|---|---|
| Server state (API data) | TanStack Query v5 | Cache, refetch, mutations |
| Auth state (user + tokens) | Zustand `authStore` | In-memory AT, localStorage RT |
| Cart count (badge) | Zustand `cartStore` | Item count + sessionId |
| Form state | React Hook Form + Zod | Component-local |
| UI state (modals, drawers) | Local `useState` | Component-local |

### Auth State Architecture

- **Access Token (AT):** Stored in-memory in Zustand `authStore.accessToken`. Clears on tab close. 15-minute expiry.
- **Refresh Token (RT):** Stored in `localStorage['ego_rt']`. 30-day expiry.
- **Boot sequence:** On app load, `main.tsx` reads `ego_rt` from localStorage → calls `POST /api/v1/auth/refresh` → if success, sets AT in memory + user in store.
- **401 interceptor:** Axios interceptor in `api/client.ts` — queue pattern: first 401 triggers refresh; concurrent 401s are queued and retried with the new AT.

### MUI Theme

Dark-mode streetwear aesthetic. Key overrides in `theme/`:
- `borderRadius: 0` globally — no rounded corners
- Black background (`#0A0A0A`)
- Custom typography with system fonts
- All MUI component defaults overridden to remove "Material" look

---

## 3. Database Architecture

### Primary DB: MySQL 8.0 (port 3307)

Key tables (verified from `schema_v2.sql`):

| Table | Module | Key Points |
|---|---|---|
| `users` | auth | `password_hash`, `is_active`, `is_email_verified`, `password_changed_at` |
| `refresh_tokens` | auth | `token_hash` (SHA-256), `family_id`, `revoked` |
| `categories` | catalog | `parent_id` self-ref, `level` (ROOT/GROUP/LEAF), `slug`, `code`, `is_active` |
| `category_hierarchy_links` | catalog | Cross-listing: `parent_category_id`, `child_category_id`, `is_primary`, `display_order` |
| `products` | catalog | `category_id` FK → LEAF category only, `status` (DRAFT/ACTIVE/ARCHIVED), `is_deleted` |
| `product_variants` | catalog | `sku`, `price`, `compare_at_price`, `is_active` |
| `inventory_records` | catalog | `quantity_available`, `quantity_reserved`, `version` (optimistic lock) |
| `product_attribute_types` | catalog | EAV: `name` (e.g. "Color") |
| `product_attribute_values` | catalog | EAV: `attribute_type_id`, `value` (e.g. "Black") |
| `variant_attribute_values` | catalog | EAV junction: `variant_id`, `attribute_value_id` |
| `product_images` | catalog | Cloudinary `public_id`, `secure_url`, `is_primary`, `display_order` |
| `variant_images` | catalog | Same as product_images but keyed to variant |
| `cart` | cart | Redis-backed — NOT in MySQL |
| `orders` | order | `status`, `grand_total`, `discount_amount`, `razorpay_order_id`, `razorpay_payment_id` |
| `order_items` | order | 5 snapshot fields (price, name, sku, image, variant label) |
| `order_status_history` | order | Append-only audit trail; `DELIVERED` entry is source of truth for return window |
| `return_requests` | returns | `status` (REQUESTED/APPROVED/REFUND_INITIATED/REFUND_COMPLETED/REJECTED) |
| `product_reviews` | review | UNIQUE(user_id, product_id); requires DELIVERED order |
| `wishlist_items` | wishlist | UNIQUE(user_id, variant_id) |
| `coupons` | coupon | `FLAT`/`PERCENTAGE` discount types |
| `order_coupons` | coupon | Audit table — FK to orders + coupons |
| `notification_logs` | notification | `event_type`, `status` (SUCCESS/FAILED), `message_id` |
| `user_addresses` | address | Soft delete, `is_default` flag |
| `search_outbox` | search | `status` (PENDING/DONE), polled every 5s by `SearchOutboxPoller` |

### Cache: Redis (port 6379)

| Key Pattern | Content | TTL |
|---|---|---|
| `cart:{userId}` | Hash: `{variantId}` → `{quantity}` | 7 days (refreshed on mutation) |
| `cart:anon:{sessionId}` | Same as above for anonymous users | 7 days |

### Search: Elasticsearch 9.0.1 (port 9200)

Index: `products`  
Document: `ProductDocument.java` — denormalized flat doc with:
- Full-text fields: `name` (autocomplete_analyzer), `description`, `tags`
- Category hierarchy: `categoryPath` (display names array), `categorySlugPath` (slug array)
- Facet fields: `availableSizes[]`, `availableColors[]`, `colorHexCodes[]`
- Price range: `minPrice`, `maxPrice`, `totalStock`
- Ratings: `avgRating`, `reviewCount`
- Visibility: `isActive` (always filtered on in search queries)

---

## 4. Key Data Flows

### Cart → Checkout → Order (Critical Path)

```
1. User adds item → POST /api/v1/cart/add
   └── CartService: Redis HSET cart:{userId} {variantId} {qty}
   └── InventoryReservationService.reserve(): qty_reserved += delta (optimistic lock, retry 3x)

2. User submits checkout → POST /api/v1/orders/checkout
   @Transactional {
     a. Load Redis cart → validate not empty
     b. InventoryReservationService.commit(): qty_reserved -= delta, qty_available -= delta
     c. Persist Order + OrderItems (with 5 snapshots) + OrderStatusHistory(PENDING_PAYMENT)
     d. CartService.clearCart() → Redis key deleted
     e. saveAndFlush() → return OrderDetailResponse
   }
   → Publish OrderPlacedEvent (async → SendGrid email)

3. User pays → POST /api/v1/payments/razorpay/create
   → Razorpay SDK: create payment order
   → Store razorpay_order_id on Order

4. Razorpay webhook → POST /api/v1/webhooks/razorpay
   → HMAC-SHA256 verification (JDK Mac, UTF-8 explicit)
   → Order status: PENDING_PAYMENT → CONFIRMED
   → Store razorpay_payment_id
   → Publish PaymentConfirmedEvent (async → SendGrid email)
```

### Elasticsearch Sync (Outbox Pattern)

```
Product create/update/delete
    │
    ▼ (same @Transactional)
SearchOutboxEntry saved (status=PENDING)
    │
    ▼ (every 5 seconds, separate thread)
OutboxPoller.pollAndIndex()
    ├── Read 100 PENDING entries
    ├── SearchIndexService.toDocument() → build ProductDocument from MySQL
    ├── Bulk upsert to Elasticsearch
    └── Mark entries DONE
```

### Return Flow

```
Customer → POST /api/v1/orders/{orderId}/returns
    Guards: order.status == DELIVERED
            within 7-day window (from OrderStatusHistory DELIVERED entry)
            no active return exists
    → ReturnRequest created (status=REQUESTED)

Admin → PUT /api/v1/admin/returns/{id}/review (APPROVE)
    @Transactional [status=APPROVED persisted outside]
    → razorpayClient.payments.refund() [OUTSIDE @Transactional]
    @Transactional {
        → RefundId stored
        → ReturnRequest.status = REFUND_COMPLETED
        → Order.status = REFUNDED
        → OrderStatusHistory(REFUNDED) appended
        → InventoryReservationService.restore() for each item
    }
```
