# feature-context.md — EGO Platform

> **Purpose:** Feature-by-feature reference for AI agents — what each feature does, where the code lives, and the key rules that govern it.  
> **Source-verified:** June 6, 2026. All state machine values and file paths confirmed against source.

---

## 1. Authentication

**What:** JWT-based stateless auth. Users register/login to get AT (15 min, in-memory) + RT (30 days, hashed in DB). RT rotation with family-based theft detection.

**Backend package:** `com.ego.raw_ego.auth`  
**Key files:**
- `JwtService.java` — issue, validate, extract claims
- `RefreshTokenService.java` — issue, rotate, revoke family
- `AuthService.java` — register, login, logout orchestration
- `JwtAuthenticationFilter.java` — `OncePerRequestFilter`, `passwordChangedAt` guard
- `SecurityConfig.java` — filter chain, PUBLIC_MATCHERS, CORS

**Frontend:**
- `store/authStore.ts` — Zustand: `accessToken` (memory), `user` object
- `api/client.ts` — Axios interceptor with queue pattern for concurrent 401s
- `features/auth/pages/` — Login, Register, VerifyEmail, ForgotPassword, ResetPassword

**Key rules:**
- AT stored in-memory (Zustand) — never in localStorage
- RT stored in `localStorage['ego_rt']`
- `passwordChangedAt` on User entity — any AT issued before this timestamp is rejected
- Token family revocation: using a revoked RT revokes the entire family

**Full endpoint list (source-verified from `AuthController.java`):**
- `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/me` — implemented
- `POST /auth/verify-email` — implemented (24h JWT, sets `emailVerified=true`)
- `POST /auth/resend-verification` — implemented (Bearer auth required)
- `POST /auth/forgot-password` — implemented (always 200, no user enumeration)
- `POST /auth/reset-password` — implemented (1h JWT, sets `passwordChangedAt=now`)
- `POST /auth/logout-all` — ⚠️ **NOT implemented** (`revokeAllByUser` not in source)

> **Note:** Email verification is not enforced at checkout. Users can place orders without verifying email.

> ⚠️ **CORRECTION (June 6, 2026):** An earlier checkpoint summary incorrectly stated these endpoints were unimplemented. `AuthController.java` source code confirms all except `logout-all` are implemented.

---

## 2. Categories (3-Level Hierarchy)

**What:** ROOT → GROUP → LEAF 3-level category tree. Products assigned to LEAF only. Navigation rendered as mega-menu.

**Backend package:** `com.ego.raw_ego.catalog` (category sub-domain)  
**Key files:**
- `Category.java` — self-referencing entity with `parent_id`, `level` enum (ROOT/GROUP/LEAF)
- `CategoryHierarchyLink.java` — cross-listing entity (one LEAF under multiple GROUPs)
- `CategoryService.java` — `getNavigationTree()`, `getCategoryBreadcrumbs()`, CRUD
- `CategoryController.java` — public + admin endpoints

**Frontend:**
- `features/navigation/` — `MegaMenu.tsx` (desktop: full-width 3-level panel), mobile drawer
- `features/catalog/admin/pages/AdminCategoriesPage.tsx` — admin CRUD with level badges

**Database:** `categories` table (`parent_id` self-ref, `slug`, `code`, `level`), `category_hierarchy_links` table

**Key rules:**
- Depth 0 = ROOT (no parent), Depth 1 = GROUP, Depth 2 = LEAF
- Products MUST be assigned to a LEAF category — `ProductService` validates `category.isLeaf()`
- `code` field is immutable after creation — embedded in SKUs
- Category slug auto-generated from name via `SlugUtils`

**API endpoints:**
- `GET /api/v1/categories` → 3-level tree (5 ROOTs with groups[] + leafCategories[])
- `GET /api/v1/categories/leaves` → flat list of all 99 LEAF categories
- `GET /api/v1/categories/{slug}` → single category with level, breadcrumbs
- `GET /api/v1/categories/{slug}/breadcrumbs` → up to 3-item path
- `GET/POST /api/v1/admin/categories` → admin CRUD

**ES integration:** `categorySlugPath` keyword array in `ProductDocument` — enables `term(categorySlugPath, "men")` to return ALL products under MEN (any depth).

---

## 3. Products

**What:** Full product catalog with variants (Color × Size EAV model), gallery images, inventory, and status lifecycle (DRAFT → ACTIVE → ARCHIVED).

**Backend package:** `com.ego.raw_ego.catalog`  
**Key files:**
- `Product.java`, `ProductVariant.java` — core entities
- `InventoryRecord.java` — `quantity_available`, `quantity_reserved`, `version` (optimistic lock)
- `ProductService.java` — storefront + admin product operations
- `CloudinaryService.java` — upload, delete, URL transformation
- `StockUrgencyService.java` — `lowStock` boolean, `stockUrgencyMessage` per variant

**Frontend:**
- `features/catalog/storefront/pages/ProductDetailPage.tsx` — PDP with variant selection
- `features/catalog/storefront/pages/ProductListingPage.tsx` — search results + filters
- `features/catalog/admin/pages/AdminProductDetailPage.tsx` — admin product editor

**Database tables:** `products`, `product_variants`, `inventory_records`, `product_attribute_types`, `product_attribute_values`, `variant_attribute_values`, `product_images`, `variant_images`

**Key rules:**
- Product MUST be assigned to a LEAF category
- SKU format: `EGO-{CATEGORY_CODE}-{SEQUENCE}-{COLOR}-{SIZE}` (auto-generated)
- `is_deleted = true` = soft delete (never hard-deleted)
- Cloudinary upload is NEVER inside `@Transactional`

**Cloudinary transformation sizes:**
| Name | Dimensions | Use |
|---|---|---|
| `thumbnail` | 200×250px | Cart, order summaries |
| `card` | 400×500px | Product listing grid |
| `detail` | 800×1000px | PDP main image |
| `zoom` | up to 1600×2000px | PDP lightbox |

---

## 4. Cart

**What:** Redis-backed cart with 7-day TTL. Anonymous cart (sessionId) merges into user cart on login. Every add/update triggers an inventory reservation.

**Backend package:** `com.ego.raw_ego.cart`  
**Key files:**
- `CartService.java` — Redis HSET/HGET/HDEL operations
- `InventoryReservationService.java` — `reserve()`, `release()`, `commit()`, `restore()`

**Redis key:** `cart:{userId}` — Hash: `{variantId}` → `{quantity}`  
**Anonymous key:** `cart:anon:{sessionId}` — merged on login via `POST /api/v1/cart/merge`

**Frontend:**
- `store/cartStore.ts` — `itemCount` (badge), `sessionId` (UUID in localStorage)
- `features/cart/` or `pages/CartPage.tsx`
- TanStack Query hooks in `features/*/hooks/useCart.ts`

**Key rules:**
- Cart TTL = 604800 seconds (7 days), refreshed on every mutation
- `409 Conflict` returned when `quantity > quantity_available`
- Catalog-deleted variants silently dropped on cart GET
- `commit()` is called during checkout — moves inventory from reserved to sold

---

## 5. Checkout + Coupons

**What:** Atomic single-transaction checkout: validate cart → commit inventory → persist order + items → clear cart. Optional coupon code for discount.

**Backend package:** `com.ego.raw_ego.order` + `com.ego.raw_ego.coupon`

**Checkout flow (single `@Transactional`):**
1. Load Redis cart — validate not empty
2. For each item: `InventoryReservationService.commit(variantId, qty)`
3. Apply coupon if provided (validate, compute discount, increment uses)
4. `Order` + `OrderItems` (5 snapshots) + `OrderStatusHistory(PENDING_PAYMENT)` persisted
5. `CartService.clearCart()` — Redis key deleted
6. `saveAndFlush()` → return `OrderDetailResponse`
7. (Async) `OrderPlacedEvent` → SendGrid email

**Coupon rules:**
- Types: `FLAT` (rupee deduction) or `PERCENTAGE` (% with optional `maxDiscountAmount` cap)
- `minOrderAmount` enforced before discount
- `maxUses` enforced (null = unlimited)
- `expiresAt` enforced (null = never expires)
- Single coupon per order
- `grandTotal = max(subtotal - discount, 0) + shippingTotal`

**Frontend:**
- `features/checkout/pages/CheckoutPage.tsx` — MUI Stepper (Address → Review → Pay)
- `features/checkout/pages/PaymentVerificationPage.tsx`
- `features/checkout/pages/OrderSuccessPage.tsx`

---

## 6. Orders

**What:** Full order lifecycle with an 8-state machine, append-only status history, and immutable item snapshots.

**Backend package:** `com.ego.raw_ego.order`  
**Key files:** `Order.java`, `OrderItem.java`, `OrderStatusHistory.java`, `OrderStatus.java`, `OrderService.java`, `OrderController.java`

**Order status machine (source-verified from `OrderStatus.java`):**
```
PENDING_PAYMENT → CONFIRMED → PROCESSING → SHIPPED → OUT_FOR_DELIVERY → DELIVERED (terminal)
PENDING_PAYMENT → CANCELLED (customer or admin)
CONFIRMED       → CANCELLED (admin only)
DELIVERED       → REFUNDED  (via returns module — NOT via admin status endpoint)
```

**OrderItem snapshot fields (immutable after order creation):**
- `skuSnapshot`, `productNameSnapshot`, `variantLabelSnapshot`, `primaryImageUrlSnapshot`, `unitPriceSnapshot`

**Key rules:**
- `REFUNDED` status set ONLY by returns module — never via admin status endpoint
- `OUT_FOR_DELIVERY` is the stage between `SHIPPED` and `DELIVERED` (added in current version — note: CURRENT_STATUS.md only lists 7 states but source has 8)
- Customer can cancel ONLY `PENDING_PAYMENT` orders
- `OrderStatusHistory` is append-only — the `DELIVERED` entry's `created_at` is the source of truth for the 7-day return window

**API endpoints:**
- `POST /api/v1/orders/checkout` — place order
- `GET /api/v1/orders` — customer order list
- `GET /api/v1/orders/{id}` — order detail
- `POST /api/v1/orders/{id}/cancel` — customer cancel (PENDING_PAYMENT only)
- `GET /api/v1/admin/orders` — admin order list (with status filter)
- `PUT /api/v1/admin/orders/{id}/status` — admin status update

---

## 7. Payments (Razorpay)

**What:** Razorpay integration for Indian payment processing. Frontend loads Razorpay Checkout.js, creates a payment order, and Razorpay sends a webhook on success.

**Backend package:** `com.ego.raw_ego.payment`  
**Key files:** `PaymentService.java`, `PaymentController.java`, `RazorpayConfig.java`

**Flow:**
1. `POST /api/v1/payments/razorpay/create` → creates Razorpay payment order, stores `razorpay_order_id` on EGO order
2. Frontend opens Razorpay Checkout.js modal with `razorpayOrderId`
3. On payment: Razorpay sends webhook `POST /api/v1/webhooks/razorpay`
4. Webhook: HMAC-SHA256 verified (JDK `Mac`, explicit UTF-8) → order advances to `CONFIRMED`

**Security:** Webhook HMAC uses `HttpServletRequest.getInputStream().readAllBytes()` to avoid Spring's charset transcoding. Razorpay SDK `Utils.verifyWebhookSignature()` is NOT used (charset bug on Windows).

**Idempotency:** Duplicate webhooks silently ignored if order already `CONFIRMED`.

**Frontend:**
- `features/checkout/` — `useRazorpay.ts` hook dynamically loads Checkout.js
- `payment.api.ts` — `createPaymentOrder(orderId)`

---

## 8. Returns

**What:** Customer-initiated return requests with admin approval workflow and Razorpay refund gateway.

**Backend package:** `com.ego.raw_ego.returns`  
**Key files:** `ReturnRequest.java`, `ReturnService.java`, `ReturnStatus.java`, `ReturnReason.java`, `ReturnController.java`

**Return status machine (source-verified from `ReturnStatus.java`):**
```
REQUESTED → APPROVED → REFUND_INITIATED → REFUND_COMPLETED (terminal)
REQUESTED → REJECTED (terminal)
```

> ⚠️ States `PICKUP_SCHEDULED` and `ITEM_RECEIVED` do NOT exist in the source code. Old documentation was wrong.

**Return reasons (source-verified):** `DEFECTIVE`, `WRONG_ITEM`, `SIZE_ISSUE`, `NOT_AS_DESCRIBED`, `OTHER`

**Guards (all checked before creating return):**
1. Order must exist and belong to requesting user
2. `order.status == DELIVERED`
3. Request within 7-day window (measured from `OrderStatusHistory` entry where `status=DELIVERED` — BUG-005 fix, June 6, 2026)
4. No active (non-REJECTED) return already exists for this order

**Refund flow (admin approval path):**
- Set `APPROVED` outside `@Transactional` (audit intermediate state)
- Call `razorpayClient.payments.refund()` OUTSIDE `@Transactional`
- Inside `@Transactional`: store refund ID, set `REFUND_COMPLETED`, advance order to `REFUNDED`, restore inventory

**API endpoints:**
- `POST /api/v1/orders/{orderId}/returns` — initiate return
- `GET /api/v1/orders/{orderId}/returns` — get return status
- `GET /api/v1/admin/returns` — admin list (with status filter)
- `GET /api/v1/admin/returns/{returnId}` — admin detail
- `PUT /api/v1/admin/returns/{returnId}/review` — approve or reject

---

## 9. Search (Elasticsearch)

**What:** Full-text faceted search via Elasticsearch 9.0.1. Outbox pattern for crash-safe indexing. Circuit breaker falls back to MySQL on ES failure.

**Backend package:** `com.ego.raw_ego.search`  
**Key files:** `SearchService.java`, `SearchIndexService.java`, `ProductDocument.java`, `OutboxPoller.java`, `SearchReindexJob.java`

**Query relevance boosting:** `name^5, categoryName^2, tags^2, description^1` + function-score in-stock boost (1.5×)

**Faceted aggregations returned:** sizes, colors, priceStats (min/max/avg)

**Category filtering:** `term(categorySlugPath, slug)` — works at all depths (ROOT, GROUP, LEAF)

**Outbox:** `search_outbox` table written in same transaction as product mutation → `OutboxPoller` runs every 5s

**Circuit breaker:** Any ES exception → fallback to MySQL `ProductService.searchFallback()` → response includes `fallbackMode: true`

**API endpoints:**
- `GET /api/v1/search?q=&categorySlug=&sizes=&colors=&minPrice=&maxPrice=&page=&size=&sort=`
- `GET /api/v1/search/autocomplete?q=` — edge-ngram, min 2 chars, max 5 suggestions
- `POST /api/v1/admin/search/reindex` — manual full reindex

---

## 10. Reviews

**What:** Purchase-gated product reviews. Only users with a DELIVERED order containing the product can review. One review per user per product.

**Backend package:** `com.ego.raw_ego.review`  
**Key rules:** `UNIQUE(user_id, product_id)`. Eligibility checked via JPQL joining `order_items → product_variants → products`. Auto-approved (no moderation). Rating summary includes avg and per-star breakdown (all 5 keys always present, zero-filled).

**API endpoints:**
- `POST /api/v1/products/{id}/reviews` — submit review (Customer JWT)
- `GET /api/v1/products/{id}/reviews` — list reviews (Public)
- `GET /api/v1/products/{id}/reviews/summary` — rating summary (Public)
- `DELETE /api/v1/admin/reviews/{id}` — admin delete

---

## 11. Wishlist

**What:** Variant-level wishlist backed by MySQL. Idempotent add/remove.

**Backend package:** `com.ego.raw_ego.wishlist`  
**Key:** `UNIQUE(user_id, variant_id)` — keyed to specific variant (color + size), not just product.  
**Idempotency:** Add returns 200 (not 409) if already present. Remove returns 200 if not present.  
**Live data on GET:** Batch-fetches variant catalog data in one SQL query — no N+1.

**API endpoints:**
- `GET /api/v1/wishlist` — list (Customer JWT)
- `POST /api/v1/wishlist/items` — add `{variantId}` (Customer JWT)
- `DELETE /api/v1/wishlist/items/{variantId}` — remove (Customer JWT)
- `DELETE /api/v1/wishlist` — clear all (Customer JWT)

---

## 12. Notifications (SendGrid)

**What:** Async transactional emails via SendGrid. Sent on `ORDER_PLACED` and `PAYMENT_CONFIRMED` events. Never blocks HTTP response.

**Backend package:** `com.ego.raw_ego.notification`  
**Flow:** Service publishes `ApplicationEvent` → `@Async @EventListener` on `ego-async-*` thread → `NotificationService` builds HTML email → SendGrid API call → `notification_logs` record written.

**Idempotency:** `existsByOrderIdAndEventTypeAndStatus(SUCCESS)` check prevents duplicate sends.  
**Error resilience:** All failures written to `notification_logs` as `FAILED`. Order/payment transaction unaffected.

**⚠️ No frontend UI** — notifications are backend-only async operations.

---

## 13. Admin Portal

**What:** Role-guarded (`ROLE_ADMIN`) management interface for products, categories, orders, returns, inventory, coupons, and users.

**Frontend:** All admin pages under `/admin/**` behind `AdminRoute` — redirects non-ADMIN users to `/403`.

**Admin pages (source-verified from router):**
- `AdminDashboardPage` — KPI metrics cards
- `AdminCategoriesPage` — 3-level category tree CRUD, level badges (ROOT/GROUP/LEAF)
- `AdminProductsPage` + `AdminProductDetailPage` — product CRUD with image upload
- `AdminOrdersPage` + `AdminOrderDetailPage` — order management, status transitions
- `AdminReturnsPage` + `AdminReturnDetailPage` — return approval/rejection
- `AdminInventoryPage` — inventory management
- `AdminCouponsPage` — coupon CRUD
- `AdminUsersPage` + `AdminUserDetailPage` — user list, suspend/activate

**Security:** All `/admin/**` backend routes require `ROLE_ADMIN` JWT. Frontend check is UX-only — backend is the enforcement layer.

---

## 14. Address Book

**What:** User-managed shipping addresses with default flag and XSS sanitization.

**Backend package:** `com.ego.raw_ego.address`  
**Security:** `HtmlSanitizer.sanitize()` applied to all free-text fields (`addressLine1`, `addressLine2`, `landmark`, `city`, `country`, `fullName`) at service layer write time (BUG-004 fix, June 6, 2026).  
**Validation:** Bean Validation `@Pattern` on `fullName` and `phone` (first line of defence). `HtmlSanitizer` is second line.

**API endpoints:**
- `GET /api/v1/addresses` — list user's addresses
- `POST /api/v1/addresses` — create address
- `PUT /api/v1/addresses/{id}` — update address
- `DELETE /api/v1/addresses/{id}` — soft-delete
- `PATCH /api/v1/addresses/{id}/default` — set as default
