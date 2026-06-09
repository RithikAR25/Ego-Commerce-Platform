# Current Status — EGO Platform

This document captures the current development status, completed modules, resolved blockers, and immediate roadmap steps as of **May 29, 2026**.

---

## 1. Project Implementation Roadmap Status

| Phase | Module Name | Scope | Status |
|---|---|---|---|
| **Phase 1** | Common Infrastructure | Docker Compose (MySQL, Redis, ES), common layers, exception hierarchies, ApiResponse envelopes. | ✅ Completed |
| **Phase 2** | Authentication | User registration, login, state, stateless JWT, Refresh Token rotation, Family Revocation, Axios interceptors. | ✅ Completed |
| **Phase 3** | Catalog Module | Category trees, Product DRAFT/ACTIVE lifecycles, attribute types/values, ProductVariants, auto-SKU generation. | ✅ Completed |
| **Phase 3.5** | Cloudinary Image Upload | Gallery + variant image upload, CDN transformation URLs, primary image logic, reorder, delete. E2E verified. | ✅ Completed & Tested |
| **Phase 4** | Elasticsearch Search | Faceted search, ES index sync, nightly reindex job. | ✅ Completed & E2E Verified (May 28, 2026) |
| **Phase 5** | Cart & Inventory Holds | Redis cart cache, transactional inventory holds (reserve/release/commit). | ✅ Completed & Tested |
| **Phase 6** | Order Module | Checkout pipeline, status history state machine, invoice mapping. | ✅ Completed & E2E Verified |
| **Phase 7** | Razorpay Integration | Orders API, Webhook verification, HMAC-SHA256, transaction idempotency. | ✅ Completed & E2E Verified (Section 6 live flow pending ngrok) |
| **Phase 8** | Notifications | SendGrid integrations, dynamic templates, async notification logs. | ✅ Completed & E2E Verified |
| **Phase 9** | Storefront Hardening | Purchase-gated reviews, wishlists, stacked checkout coupons. | ✅ Completed & E2E Verified |
| **Phase 10** | Return & Refund | Return requests, Razorpay refund gateway integrations. | ✅ Completed — Sections A/B/C/E/F E2E Verified. Section D (Razorpay gateway) deferred — requires real `pay_xxx` from Checkout.js (Phase 12). |
| **Phase 11** | Administrative Portal | Dashboard KPI metrics, full CRUD management panels. | ✅ Completed |
| **Phase 12** | Consumer Storefront | Product Detail Page (PDP), dynamic selectors, Stripe/Razorpay modals. | ✅ Completed |
| **Phase 13** | Admin Dashboard Client | Protected management panels, statistics graphs. | ✅ Completed |
| **Phase 14** | Elasticsearch Search (reinstated) | Faceted search, autocomplete, ES sync listeners, nightly reindex. | ✅ Completed & E2E Verified (May 28, 2026) — merged with Phase 4 |
| **Phase 15** | Production Deployment | Github CI/CD, multi-stage Docker builds, VPS security setups. | ⏳ Pending |
| **Phase 16** | Platform Hardening | Input sanitization, load testing, security headers. | ⏳ Pending |


---

## 2. Completed Modules & Capabilities

### Phase 1: Common Layer
* Standardized API response structure (`ApiResponse<T>`) and exception mapping using `@RestControllerAdvice`.
* Global error handling configured for structured validation error arrays (`ApiError[]`).

### Phase 2: Authentication (Hardened)
* **JWT token pairs:** Access tokens (15-min TTL, stored in-memory) + Refresh tokens (30-day TTL, SHA-256 hashed in database).
* **Silent refresh mechanism:** Atomic Axios interceptor queue in the frontend (`client.ts`) handles automatic token rotation, preventing simultaneous requests from trigger family reuse revocations.
* **Theft detection:** Reusing a rotated refresh token triggers instant database-driven revocation of the entire token family.
* **Resolved Issues:** Logout race conditions, Axios 1.x type safety, and global MUI snakbar notifications are fully verified and integrated.

### Phase 3: Catalog Module
* **Category mapping:** Enforces 2-level hierarchy depth via service-level depth validation.
* **Slugification:** Automatic SEO slug generation through `SlugUtils` with uniqueness protection.
* **Product Variant EAV:** Extended Attribute-Value system mapping variant options (Color × Size) to a normalized schema.
* **Auto-SKU generator:** Server-side compiler resolves uppercase identifiers (`EGO-MEN-0001-BLK-M`).
* **Cascade mapping resolved:** Variant-to-Inventory saves are configured atomically through standard hibernate parent-child cascade lifecycle rules.
* **Sorting Fallbacks:** Invalid sort queries (e.g., Swagger UI default `["string"]` parameters) are gracefully parsed as `400 Bad Request` instead of failing as a 500 error, using custom Spring exceptions.

### Phase 3.5: Cloudinary Image Upload System ✅ (Completed & E2E Tested — May 23 2026)
* **CloudinaryConfig:** Spring `@Bean` wiring Cloudinary SDK from environment variables. Docker-compatible.
* **CloudinaryService:** Infrastructure service for upload, delete, and transformation URL generation. Deliberately NOT `@Transactional` — keeps DB connections free during external HTTP calls.
* **ProductImageService:** Gallery image lifecycle (upload orchestration, DB persistence, delete, reorder).
* **VariantImageService:** Variant image lifecycle with primary image enforcement rules (auto-promote first, atomic primary transfer, auto-promote on delete).
* **ProductImageController:** Gallery image REST API — public GET + admin POST/DELETE/PUT/reorder.
* **VariantImageController:** Variant image REST API — public GET + admin POST/DELETE/PATCH(primary)/PUT/reorder.
* **ImageResponse extended:** Added `Transformations` inner class (thumbnail, card, detail, zoom URLs). Non-breaking — existing callers unaffected.
* **ImageUploadRequest / ReorderImagesRequest:** New request DTOs with Jakarta validation.
* **ImageUploadException:** New domain exception mapped to `500 Internal Server Error`.
* **Transformation strategy:** `f_auto,q_auto` for WebP/AVIF auto-delivery. Four standard sizes pre-generated as eager transformations at upload time.
* **application.properties:** Cloudinary env var bindings, 5MB multipart limits.
* **Documentation:** `/docs/integrations/cloudinary.md`, `/docs/backend/product-images.md`, `/docs/frontend/image-rendering.md`, `/docs/database/product-images-schema.md`, `API_CONTRACTS.md` updated.
* **E2E Testing:** All happy-path and negative test cases verified via Swagger UI + curl on May 23 2026. First live upload confirmed: `ego/dev/products/2/gallery/rvtcsfaqa7ie6enolthu`. All 4 transformation URLs resolving. DB record correct.
* **SDK Bugs Resolved:**
  * `@RequestPart` for JSON DTO rejected by Swagger/curl (`application/octet-stream`) → fixed to flat `@RequestParam` fields.
  * `eager` param in `cloudinary-http5 v2.3.2` requires `List<Transformation>` objects (not `String`, not `List<String>`) → fixed using SDK fluent `Transformation` builder API.
* **Testing Guide:** `/docs/testing/cloudinary-image-upload-testing-guide.md` — includes full test checklist, historic errors, and verified working response.

---

### Phase 5: Cart & Inventory Holds ✅ (Completed & E2E Tested — May 24 2026)

* **Redis Infrastructure:**
  * `spring-boot-starter-data-redis` (Lettuce driver) added to `pom.xml`.
  * `RedisConfig` bean: `RedisTemplate<String, String>` with `StringRedisSerializer` for both keys and hash fields — human-readable via `redis-cli`.
  * Redis connection reads from `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` env vars (defaults: `localhost:6379`).
  * Cart TTL: 7-day expiry, refreshed on every cart mutation.

* **Cart Module (`com.ego.raw_ego.cart`):**
  * **`CartService`:** Redis hash cart — key `cart:{userId}`, fields `{variantId} → {quantity}`. Batch variant fetch from MySQL (`findAllById`) — zero N+1 queries. Silently drops catalog-deleted variants on read. Anonymous cart merge on login (`cart:anon:{sessionId}` → `cart:{userId}`).
  * **`InventoryReservationService`:** `reserve()` / `release()` / `commit()` lifecycle. Optimistic lock retry loop (max 3 attempts) via `adjustReservedWithOptimisticLock` JPQL query. Returns `409 Conflict` on persistent contention.
  * **`CartController`:** 6 REST endpoints — `GET`, `POST /add`, `PUT /items/{variantId}`, `DELETE /items/{variantId}`, `DELETE` (clear), `POST /merge`. All require authentication via existing Spring Security `anyRequest().authenticated()` rule.
  * **`InventoryRecordRepository`:** Added `adjustReservedWithOptimisticLock` JPQL `@Modifying` query — safe signed-delta with version guard and non-negativity check.

* **Response DTOs:**
  * `CartItemResponse`: `variantId`, `sku`, `productName`, `variantLabel` (e.g. "Black / M"), `price`, `compareAtPrice`, `discountPercent`, `primaryImageUrl`, `quantity`, `stockStatus`, `quantityAvailable`.
  * `CartResponse`: `items[]`, `itemCount` (total units — badge count), `subtotal` (server-computed live price sum).

* **Frontend Data Layer:**
  * `cart.types.ts`: TypeScript interfaces strictly mirroring backend DTOs.
  * `cart.api.ts`: 6 API functions through the existing `apiClient` (JWT interceptor applies automatically).
  * `cartStore.ts` (Zustand): `itemCount` (Navbar badge) + `sessionId` (anonymous cart UUID via `crypto.randomUUID()`, persisted to `localStorage['ego_session_id']`).
  * `useCart.ts` (TanStack Query): 6 hooks — `useCart`, `useAddToCart`, `useUpdateCartItem`, `useRemoveCartItem`, `useClearCart`, `useMergeCart`. Uses `setQueryData` for instant UI updates (no loading flash on mutations).
  * `useAuth.ts` (modified): `useLogin` and `useRegister` `onSuccess` handlers now call `mergeCart` immediately after authentication. `useLogout` `onSettled` now calls `resetCart()` to zero the badge count.

* **E2E Test Results (May 24 2026 — Swagger UI):**
  * `POST /api/v1/cart/add` `{variantId, quantity}` → `200 CartResponse` with item and correct subtotal ✅
  * `GET /api/v1/cart` → `200` with live MySQL prices and `stockStatus` ✅
  * `PUT /api/v1/cart/items/{variantId}` → `200` updated quantity; `quantity_reserved` in MySQL reflects delta ✅
  * `DELETE /api/v1/cart/items/{variantId}` → `200` item removed; reservation released ✅
  * `DELETE /api/v1/cart` → `200` cart cleared; all reservations released ✅
  * `POST /api/v1/cart/add` with `quantity > stock` → `409 Conflict` "Insufficient stock" ✅
  * `GET /api/v1/cart` unauthenticated → `401 Unauthorized` ✅
  * Redis verification: `redis-cli HGETALL cart:{userId}` shows correct `{variantId} → {quantity}` hash ✅
  * Redis TTL: `redis-cli TTL cart:{userId}` returns `604800` (7 days) ✅
  * `mvnw compile` → **0 errors** ✅
  * `tsc --noEmit` → **0 type errors** ✅

---

### Phase 11: Admin Catalog Frontend ✅ (Completed — May 23 2026)
* **Normalized Attribute System UI:** Added `AttributeManagerPanel` bridging the gap between Phase 3 backend and frontend. Enables dynamic creation of product dimensions (e.g. Color, Size).
* **Variant Orchestration:** `CreateVariantDialog` fully updated to dynamically read backend attribute schemas, generate hex color swatches, and mandate attribute completion before variant creation.
* **Cache Synchronization:** TanStack Query `invalidateQueries` accurately maps image mutations (upload/delete/primary) back to the overarching `productKeys.all()` cache to trigger seamless, flash-free UI updates.
* **Strict Type Safety:** Unified MUI v6 `slotProps` API across all forms to clear strict TS compiler errors. Aligned all frontend `catalog.types.ts` schemas strictly to the exact Java DTOs.
* **Inventory Handling:** Aligned React `SetInventoryDialog` field names strictly with `UpdateInventoryRequest.java` (`quantityAvailable`).

---

### Phase 6: Order Module ✅ (Completed & E2E Verified — May 24–25, 2026)

* **`OrderStatus` enum:** 7-state lifecycle — `PENDING_PAYMENT → CONFIRMED → PROCESSING → SHIPPED → DELIVERED | CANCELLED | REFUNDED`. Static `assertValidAdminTransition()` guard enforces forward-only state machine.
* **JPA Entities:** `Order` (optimistic lock `@Version`), `OrderItem` (5 full product snapshots), `OrderStatusHistory` (append-only audit trail). All in `com.ego.raw_ego.order.entity`.
* **Snapshot fields on `OrderItem`:** `skuSnapshot`, `productNameSnapshot`, `variantLabelSnapshot`, `primaryImageUrlSnapshot`, `unitPriceSnapshot` — immutable forever. Protects order history from catalog mutations (renames, image deletes, price changes).
* **`OrderRepository`:** Ownership-safe `findByIdAndUserId` (returns 404 not 403 — avoids order ID enumeration). Nullable status filter admin query (`findAllByStatusFilter`).
* **`OrderService.checkout()` — atomic pipeline (single `@Transactional`):**
  1. Load Redis cart → validate not empty, all items purchasable
  2. `InventoryReservationService.commit()` for each item
  3. Persist `Order` + `OrderItems` + initial `OrderStatusHistory(PENDING_PAYMENT)`
  4. `CartService.clearCart()` — Redis key deleted
  5. Return `OrderDetailResponse`
* **`OrderService.cancelOrder()`:** Only `PENDING_PAYMENT` cancellable. Calls new `InventoryReservationService.restore()` for each line item to return `quantity_available`.
* **`InventoryReservationService.restore()`:** New method — inverse of `commit()`. Adds `+delta` back to `quantity_available` via `adjustQuantityWithOptimisticLock` with retry loop.
* **`OrderController`:** 6 REST endpoints — 4 customer (`checkout`, `list`, `detail`, `cancel`) + 2 admin (`list`, `status update`). Follows exact same `@AuthenticationPrincipal` + `UserRepository` pattern as `CartController`.
* **Database DDL:** `schema_order_module.sql` — 3 tables with inline documentation (`orders`, `order_items`, `order_status_history`). `order_items.primary_image_url_snapshot` column included.
* **Frontend Data Layer:**
  * `order.types.ts`: Full TypeScript interfaces for all 4 response DTOs + 2 request payloads.
  * `order.api.ts`: 6 API functions (checkout, getOrders, getOrder, cancelOrder, adminGetOrders, adminUpdateOrderStatus).
  * `useOrders.ts` (TanStack Query): `useCheckout` (clears cart cache + resets badge on success), `useOrders(page)`, `useOrder(id)`, `useCancelOrder` (sets order detail cache via `setQueryData`).
* **Compile checks (May 24 2026):** `mvnw compile` → BUILD SUCCESS ✅ | `tsc --noEmit` → 0 type errors ✅
* **E2E Live Test (May 25 2026):** All 25 test cases across Sections A–E passed ✅
  * **Section A** — Checkout: 201 response, cart cleared, inventory committed, all 3 DB tables populated with correct data
  * **Section B** — Order reads: list pagination, order detail with full snapshots
  * **Section C** — Cancellation: CANCELLED status, inventory restored, 2-entry status history
  * **Section D** — Admin: full lifecycle PENDING_PAYMENT → CONFIRMED → PROCESSING → SHIPPED → DELIVERED; status filter
  * **Section E** — Negative cases: 409 empty cart, 400 validation, 409 cancel confirmed, 404 cross-user, 401 no auth, 403 customer→admin, 400 invalid transition, 404 not found
* **6 bugs found and fixed during E2E testing** (see details below)

#### Bugs Found & Fixed During E2E Testing

**Bug 1 — `commit()` did not guard against zero `quantity_reserved` (InventoryReservationService.java)**
- **Symptom:** `409 — "Unable to update inventory reservation after 3 attempts"` on every checkout attempt.
- **Root cause (surface):** `commit()` always called `release()` even when `quantity_reserved = 0`. The `adjustReservedWithOptimisticLock` guard `quantity_reserved + delta >= 0` → `0 + (-2) = -2` → WHERE clause never matched → 0 rows → retried 3× → threw 409.
- **Fix:** Added a guard in `commit()` — re-reads the fresh record after the `quantity_available` decrement and only calls `release()` if `quantityReserved >= delta`. Silently skips with a WARN log if not (the available decrement is the actual oversell guard; reserved is accounting-only).

**Bug 2 — Hibernate first-level cache not cleared after bulk JPQL UPDATEs (InventoryRecordRepository.java)** ← _root cause of Bug 1 retries_
- **Symptom:** Same 409. Even after Bug 1 fix, the retry loop in `adjustReservedWithRetry` kept failing.
- **Root cause:** Both `@Modifying` queries (`adjustQuantityWithOptimisticLock`, `adjustReservedWithOptimisticLock`) were missing `clearAutomatically = true`. After a bulk JPQL UPDATE bumps the `version` column in the DB, Hibernate's 1st-level entity cache still holds the pre-update entity with the old `version`. All subsequent `findByVariantId()` calls in the same `@Transactional` boundary return the stale entity. The optimistic lock `WHERE version = :expectedVersion` therefore never matches the DB version → 0 rows on every attempt.
- **Fix:** Added `@Modifying(clearAutomatically = true)` to both bulk UPDATE queries. Hibernate now flushes and evicts all entities from the 1st-level cache after each bulk UPDATE, so the retry loop's `findByVariantId()` reads fresh version numbers from the DB.

**Bug 3 — `null` id/timestamps in checkout response (OrderService.java)**
- **Symptom:** `id: null`, `createdAt: null`, `updatedAt: null` in `OrderDetailResponse` even though the order was successfully created in DB.
- **Root cause:** `save()` was used instead of `saveAndFlush()`. Within the same `@Transactional` method, `@CreationTimestamp` / `@UpdateTimestamp` values are written to the DB during flush, but the Java entity object's fields may not reflect the DB-generated values until the flush is explicitly triggered and the entity re-read.
- **Fix:** Changed to `Order savedOrder = orderRepository.saveAndFlush(order)` and used `savedOrder` in the DTO mapper. `saveAndFlush()` forces an immediate DB roundtrip, ensuring all generated values (`@Id`, `@CreationTimestamp`, `@UpdateTimestamp`, cascade-child `@Id`s) are populated in the entity before mapping.

**Bug 4 — `LazyInitializationException` on order cancellation (OrderService.java)**
- **Symptom:** `POST /orders/{id}/cancel` returned 500 with `LazyInitializationException: Cannot lazily initialize collection of role 'Order.statusHistory' — no session`.
- **Root cause:** `cancelOrder()` called `reservationService.restore()` which internally executed `@Modifying(clearAutomatically=true)`. This called `em.clear()`, evicting ALL entities from the Hibernate 1st-level cache — including the `order` entity currently being mutated. After eviction, `order` was detached. The subsequent `order.addStatusHistory(...)` tried to lazy-load `statusHistory` on the detached entity → exception.
- **Fix:** (1) Force-initialize `order.getItems()` into a plain `ArrayList` before any `restore()` call. (2) After all restore() calls, re-fetch a fresh managed `Order` via `findByIdAndUserId()`. Use the fresh entity for status update and DTO mapping.

**Bug 5 — `PENDING_PAYMENT → CONFIRMED` rejected by admin state machine (OrderStatus.java)**
- **Symptom:** Admin `PUT /orders/{id}/status` with `{ "status": "CONFIRMED" }` returned 400 `Invalid status transition: PENDING_PAYMENT → CONFIRMED`.
- **Root cause:** `assertValidAdminTransition()` switch had `PENDING_PAYMENT` hitting the `default → false` case. The original design assumed only the Phase 7 Razorpay webhook would set `CONFIRMED`, so admin was blocked from doing so manually.
- **Fix:** Added `case PENDING_PAYMENT -> next == CONFIRMED || next == CANCELLED` to the switch. Admins can now manually confirm (COD, bank transfer) or cancel unpaid orders. Phase 7 webhook will use the same `PENDING_PAYMENT → CONFIRMED` path.

**Bug 6 — `null` createdAt on new status history entries in adminUpdateStatus (OrderService.java)**
- **Symptom:** After `PUT /admin/orders/{id}/status`, the newly appended `statusHistory` entry had `createdAt: null`.
- **Root cause:** `adminUpdateStatus()` used `return toDetailResponse(order)` without flushing. The new `OrderStatusHistory` child entity was added to the collection but not yet flushed to DB, so its `@CreationTimestamp` hadn't been set.
- **Fix:** Changed to `Order savedOrder = orderRepository.saveAndFlush(order)` and used `savedOrder` for DTO mapping — same pattern as `checkout()`.

---

### Phase 7: Razorpay Integration ✅ (Completed & E2E Verified — May 25–26, 2026)

* **`RazorpayConfig`:** Spring `@Bean` wiring `RazorpayClient` from `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` env vars.
* **`PaymentService.createRazorpayOrder()`:** Validates EGO order ownership + `PENDING_PAYMENT` status. Creates Razorpay payment order via SDK. Persists `razorpay_order_id` to DB. Idempotent — second call returns the existing Razorpay order without creating a new one.
* **`PaymentService.handleWebhook()`:** HMAC-SHA256 signature verification (explicit UTF-8, JDK `Mac` — NOT Razorpay SDK `Utils` which uses platform-default charset). Processes `payment.captured` events only. Advances EGO order to `CONFIRMED`, stores `razorpay_payment_id`. Idempotent — duplicate webhooks silently ignored.
* **`PaymentController`:** Two endpoints:
  * `POST /api/v1/payments/razorpay/create` — JWT-secured (customer)
  * `POST /api/v1/webhooks/razorpay` — public, HMAC-secured. Reads raw bytes via `HttpServletRequest.getInputStream()` to avoid Spring `StringHttpMessageConverter` charset ambiguity.
* **`Order` entity:** Added `razorpay_order_id VARCHAR(100)` (nullable, indexed via `@Table @Index`) and `razorpay_payment_id VARCHAR(100)` (nullable, no index — never queried by this column).
* **`OrderRepository`:** Added `findByRazorpayOrderId(String)` for webhook lookup.
* **`OrderDetailResponse`:** Added `razorpayOrderId` field — populated after payment initiation.
* **SecurityConfig:** Added `/api/v1/webhooks/**` to `PUBLIC_MATCHERS`.
* **Frontend Data Layer:**
  * `order.types.ts`: Added `razorpayOrderId` to `OrderDetail`, new `PaymentOrderResponse` and `CreatePaymentOrderPayload` interfaces.
  * `payment.api.ts`: `createPaymentOrder()` function for `POST /payments/razorpay/create`.
  * `useRazorpay.ts`: Custom hook — dynamically loads Checkout.js, exposes `openPaymentModal()` with typed success/error callbacks. Full TypeScript declarations for `window.Razorpay`.
  * `useOrders.ts`: Added `useCreatePayment` TanStack Query mutation hook.
* **Database DDL:** `docs/database/schema_razorpay_columns.sql` — `ALTER TABLE` + `CREATE INDEX`.
* **Compile checks (May 25 2026):** `mvnw compile` → BUILD SUCCESS (108 files) ✅ | `tsc --noEmit` → 0 type errors ✅
* **E2E Testing (May 25–26 2026):** 21/22 checks verified ✅
  * Section 2: EGO order placement + DB verification
  * Section 3: Payment order creation (A-1 to A-4) — `razorpayOrderId = order_StWAmGXFqppm6W` confirmed
  * Section 4: Error cases B-1 to B-5 (404, 409, 400, 401)
  * Section 5: Webhook C-1 to C-6 — HMAC verification, idempotency, event filtering, error rejection
  * Section 7: Post-confirmation admin flow (D-1, D-2)
  * Section 8: Edge cases E-1, E-2
  * Section 9: DB final state verified
  * Section 6 (live Checkout.js → ngrok → real Razorpay webhook): pending ngrok setup
* **Testing Guide:** `docs/testing/razorpay-payment-e2e-testing-guide.md`

#### Bug Found & Fixed During Phase 7 E2E Testing

**Bug 7 — HMAC signature mismatch (PaymentService.java)**
- **Symptom:** All webhook calls returned `"Invalid Razorpay webhook signature."` even with correctly computed PowerShell HMAC.
- **Root cause (1):** `Utils.verifyWebhookSignature()` in Razorpay SDK v1.4.5 calls `payload.getBytes()` and `secret.getBytes()` without specifying a charset. On Windows, JVM default charset is `windows-1252` (CP1252), not UTF-8. This causes the HMAC to be computed over differently-encoded bytes vs PowerShell's `UTF8.GetBytes()`.
- **Root cause (2):** Spring's `@RequestBody String` via `StringHttpMessageConverter` can silently transcode `application/json` body to ISO-8859-1 when no explicit `charset=` is present in Content-Type, altering the bytes before HMAC computation.
- **Fix (1):** Replaced `Utils.verifyWebhookSignature()` with custom JDK `Mac.getInstance("HmacSHA256")` using `StandardCharsets.UTF_8` for both key and payload byte conversion.
- **Fix (2):** Changed controller from `@RequestBody String rawBody` to `HttpServletRequest.getInputStream().readAllBytes()` with explicit `new String(bytes, StandardCharsets.UTF_8)` — zero charset ambiguity.
- **Debug logging added:** `[Webhook HMAC Debug]` log lines output `payload_bytes`, `secret_bytes`, `received_sig`, `computed_sig`, `match` for diagnosis. Remove after confirmed working.

---

### Phase 8: SendGrid Notifications ✅ (Completed & E2E Verified — May 26, 2026)

* **`sendgrid-java:4.10.2`:** Added to `pom.xml`. SendGrid Spring `@Bean` wired in `SendGridConfig.java`.
* **`@EnableAsync`:** Added to `RawEgoApplication.java`. Async thread pool configured via `spring.task.execution.*` properties (`ego-async-*` threads).
* **`NotificationLog` entity:** Maps to `notification_logs` table. Stores `order_id`, `recipient_email`, `event_type`, `status`, `error_message` (failures), `message_id` (SendGrid X-Message-Id header), `created_at`.
* **`NotificationEventType` / `NotificationStatus` enums:** `ORDER_PLACED`, `PAYMENT_CONFIRMED` / `SUCCESS`, `FAILED`.
* **`OrderPlacedEvent` / `PaymentConfirmedEvent`:** Spring `ApplicationEvent` subclasses published after `saveAndFlush()` in `OrderService.checkout()` and `PaymentService.confirmOrder()`.
* **`NotificationLogService`:** Persists log rows in `@Transactional(REQUIRES_NEW)` — always commits independently, survives even if the caller has no active transaction.
* **`NotificationService`:** Core email builder + dispatcher. NOT `@Transactional` (per architecture rules). Builds premium inline-HTML emails with EGO dark-theme branding, INR amounts, order items table, shipping address. Checks idempotency before every send. Catches all exceptions internally — failures never propagate to caller.
* **`NotificationEventListener`:** `@Async @EventListener` — dispatches emails on `ego-async-*` threads. HTTP response thread is never blocked.
* **Trigger wiring:**
  * `OrderService.checkout()` → publishes `OrderPlacedEvent` → `sendOrderConfirmation()` → "Your EGO order #N has been received!"
  * `PaymentService.confirmOrder()` → publishes `PaymentConfirmedEvent` → `sendPaymentConfirmation()` → "Payment confirmed — EGO order #N is being prepared"
* **Idempotency:** `existsByOrderIdAndEventTypeAndStatus(SUCCESS)` check prevents duplicate emails on repeated events.
* **Error resilience:** All SendGrid errors written to `notification_logs` as `FAILED` rows. Order/payment transactions unaffected.
* **Real API key configured:** `SENDGRID_API_KEY` and `SENDGRID_FROM_EMAIL=dev-email@ego.com` in `.env`.
* **Documentation:** `docs/integrations/sendgrid.md`, `docs/testing/sendgrid-notifications-testing-guide.md`, `docs/database/schema_notification_logs.sql`.

**Post-verification bugs fixed during E2E (May 26, 2026):**

* **`LazyInitializationException` on `ego-async-1`:** `NotificationService` called `orderRepository.findById()` which left `User` and `items` as uninitialized LAZY proxies after the session closed. Fixed by adding `findByIdWithUserAndItems(@Query JOIN FETCH)` to `OrderRepository` — loads the full graph in one SQL query within the async thread's own session.
* **`UnknownFormatConversionException: Conversion = '"'`:** HTML builders used `.formatted()` on strings containing `width="100%"` — the `%"` sequence was parsed as an invalid format conversion. Fixed by rewriting all HTML builders to use direct `StringBuilder.append()` concatenation. `String.format()` / `.formatted()` no longer used for HTML content.
* **`WebhookSignatureException` (new class):** Invalid HMAC signatures previously threw `ConflictException` → HTTP 409. Created dedicated `WebhookSignatureException` → HTTP 400 Bad Request. Razorpay only retries on 5xx — 400 is the correct rejection signal.
* **Idempotency log hardened:** Duplicate webhook delivery (valid signature, order already CONFIRMED) now logs `[Webhook] Duplicate delivery detected (idempotent skip)` and returns `200 OK` — production-correct Razorpay retry contract.
* **PowerShell/curl HMAC mismatch documented:** Documented root cause (byte-level encoding differences in PS/curl transmission) and recommended workaround (use backend `computed_sig` from debug log) in Razorpay testing guide Appendix. Confirmed backend HMAC implementation is correct.


---

### Phase 9: Storefront Hardening ✅ (Completed & E2E Verified — May 27, 2026)

#### 9A — Purchase-Gated Product Reviews
* **Entity:** `ProductReview` — `product_reviews` table with `UNIQUE(user_id, product_id)` constraint.
* **Eligibility gate:** Repository JPQL query bridges `order_items → product_variants → products` without cross-module entity coupling. Only users with a `DELIVERED` order containing the product can review.
* **One review per product per user.** Auto-approved (no moderation queue).
* **Endpoints:** `POST /api/v1/products/{id}/reviews` (Customer JWT), `GET /api/v1/products/{id}/reviews` (Public), `GET /api/v1/products/{id}/reviews/summary` (Public, returns avg + breakdown 1-5), `DELETE /api/v1/admin/reviews/{id}` (Admin).
* **Rating summary:** `ProductRatingSummary` — avg rating (1 decimal), total count, star breakdown map (all 5 keys always present, zero-filled).

#### 9B — Wishlists
* **Entity:** `WishlistItem` — `wishlist_items` table with `UNIQUE(user_id, variant_id)` constraint.
* **Storage:** MySQL (durable, not Redis). Keyed by `variant_id` (specific color/size), not product.
* **Idempotency:** Add returns 200 OK (not 409) if variant already in wishlist. Remove returns 200 OK if variant not present.
* **Live data on GET:** Batch-fetches variant catalog data (price, stock, image) in one SQL query — no N+1 queries. Silently drops catalog-deleted variants on read.
* **Endpoints:** `GET /api/v1/wishlist` (Customer JWT), `POST /api/v1/wishlist/items` (Customer JWT), `DELETE /api/v1/wishlist/items/{variantId}` (Customer JWT), `DELETE /api/v1/wishlist` (Customer JWT).

#### 9C — Coupon Codes at Checkout
* **Entities:** `Coupon` — `coupons` table. `OrderCoupon` — `order_coupons` audit table.
* **Discount types:** `FLAT` (rupee amount) and `PERCENTAGE` (percent, optional cap via `maxDiscountAmount`).
* **Constraints:** `minOrderAmount` (nullable), `maxUses` (nullable = unlimited), `expiresAt` (nullable = never expires).
* **Single coupon per order.** Coupon code is case-insensitive (normalized to UPPER on write).
* **Checkout integration:** `CheckoutRequest` now accepts optional `couponCode`. Validation, discount computation, `OrderCoupon` persistence, and `currentUses` increment all happen atomically inside the existing `@Transactional checkout()` boundary. Invalid coupon rolls back inventory commits.
* **Order entity:** Added `discount_amount` (DECIMAL, default 0) and `coupon_code_snapshot` (VARCHAR) columns to `orders` table.
* **Discount formula:** `grandTotal = max(subtotal - discount, 0) + shippingTotal`.
* **Soft delete:** Admin deactivates coupons (`active=false`); never hard-deleted (preserves `order_coupons` FK integrity).
* **Public validate endpoint:** `GET /api/v1/coupons/validate?code=X&subtotal=Y` — preview discount with zero side effects.
* **Endpoints:** Validate (Public), Admin CRUD (`POST/GET/PUT/DELETE /api/v1/admin/coupons`).
* **SecurityConfig:** `/api/v1/coupons/validate` added to `PUBLIC_MATCHERS`.
* **Compile verified:** `mvnw compile` → **0 errors**.
* **E2E verified:** All review, wishlist, and coupon test scenarios passed (May 27, 2026).
* **Schema SQL:** `docs/database/schema_coupons.sql` (includes `orders` ALTER for new columns).
* **Testing guide:** `docs/testing/phase9-storefront-hardening-testing-guide.md`.

---

### Phase 10: Return & Refund ✅ (Completed — May 27, 2026)

#### 10A — Return Request Flow
* **Entity:** `ReturnRequest` — `return_requests` table. Fields: `order_id`, `requested_by`, `reason` (enum), `reason_detail`, `status`, `refund_amount`, `razorpay_refund_id`, `admin_notes`, `version` (optimistic lock).
* **Return reasons:** `ReturnReason` enum — `DEFECTIVE`, `WRONG_ITEM`, `SIZE_ISSUE`, `NOT_AS_DESCRIBED`, `OTHER`.
* **State machine:** `ReturnStatus` enum — `REQUESTED → APPROVED → REFUND_INITIATED → REFUND_COMPLETED` | `REJECTED` (terminal). Static `assertValidAdminTransition()` guard.
* **Service guards (in order):** (1) Order exists + owned by user, (2) Order status = `DELIVERED`, (3) Return within 7-day window (checked against `order.updatedAt`), (4) No active (non-REJECTED) return already exists.
* **Idempotency:** `existsByOrderIdAndStatusNot(REJECTED)` prevents duplicate submissions. After rejection, customer may resubmit.

#### 10B — Admin Approval/Rejection Workflow
* **Rejection path:** Sets return status → `REJECTED`. Order status unchanged. No Razorpay call.
* **Approval path (architecture-compliant):**
  1. Validate `refundAmount > 0` and `≤ order.grandTotal`.
  2. Validate `order.razorpay_payment_id` exists (cannot refund cash orders).
  3. Set status → `APPROVED`, persist outside `@Transactional` (intermediate audit state).
  4. Call `razorpayClient.payments.refund()` — **OUTSIDE `@Transactional`** per architecture rules.
  5. On success: inside `@Transactional` — store `razorpayRefundId`, set return status → `REFUND_COMPLETED`, advance `order.status → REFUNDED`, append `OrderStatusHistory(REFUNDED)`, restore inventory for all line items.

#### 10C — Razorpay Refund Gateway
* **API call:** `razorpayClient.payments.refund(razorpayPaymentId, { amount_in_paise, speed: "normal" })`.
* **Architecture rule compliance:** Razorpay HTTP call made strictly outside `@Transactional` — DB connection free during external call.
* **Refund ID stored:** `razorpay_refund_id` on `return_requests` (format: `rfnd_XXXXXXXXXXXXXXXXXX`).
* **Partial refunds supported:** Admin specifies any `refundAmount ≤ order.grandTotal`.

#### 10D — Inventory Restoration
* Uses existing `InventoryReservationService.restore(variantId, quantity)` — same method as Phase 6 cancellation.
* Items pre-snapshotted into plain `ArrayList` before `restore()` calls to avoid `LazyInitializationException` from `@Modifying(clearAutomatically=true)` — same defensive pattern as Phase 6 `cancelOrder()`.
* After all restore calls, entities re-fetched fresh (post `em.clear()`) before final DB writes.

#### 10E — API Surface (5 endpoints)
| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/orders/{orderId}/returns` | Customer JWT |
| GET | `/api/v1/orders/{orderId}/returns` | Customer JWT |
| GET | `/api/v1/admin/returns` | ROLE_ADMIN |
| GET | `/api/v1/admin/returns/{returnId}` | ROLE_ADMIN |
| PUT | `/api/v1/admin/returns/{returnId}/review` | ROLE_ADMIN |

#### 10F — Frontend Data Layer
* `src/types/return.types.ts`: `ReturnReason`, `ReturnStatus` union types + `ReturnRequest`, `InitiateReturnPayload`, `AdminReviewReturnPayload` interfaces.
* `src/api/return.api.ts`: 5 API functions through `apiClient` (JWT interceptor auto-applies).
* `src/features/orders/hooks/useReturns.ts` (TanStack Query): 5 hooks — `useInitiateReturn`, `useOrderReturn`, `useAdminReturns`, `useAdminReturn`, `useAdminReviewReturn`.
* Query key factory `returnKeys` for predictable cache invalidation.

#### 10G — Compile \& Type Verification
* `mvnw compile` → **BUILD SUCCESS** (153 files, 0 errors) ✅
* `tsc --noEmit` → **0 type errors** ✅

#### 10H — E2E Test Results (May 27, 2026)
* **Section A** (Customer: Initiate Return) — All 6 tests ✅ PASSED
* **Section B** (Customer: Get Return Status) — Both tests ✅ PASSED
* **Section C** (Admin: Reject Return) — Both tests ✅ PASSED
* **Section D** (Admin: Approve Return / Razorpay Refund) — ⏳ DEFERRED
  * D-2 (refundAmount > grandTotal guard), D-3 (null payment ID guard), D-4 (missing refundAmount guard) — all ✅ PASSED
  * D-1 (full Razorpay refund) — deferred: webhook-simulated `razorpay_payment_id` values are not real Razorpay payment objects. Razorpay API returns `404 "no Route matched"`. Re-run after Phase 12 (real Checkout.js payment generates a genuine `pay_xxx` ID).
  * Backend orchestration, transaction handling, exception handling, and rollback protection all confirmed correct via the 409 error path.
* **Section E** (Admin: List \& Filter Returns) — All 6 tests ✅ PASSED
* **Section F** (State Machine DB Verification) — ✅ PASSED

* **Schema SQL:** `docs/database/schema_return_module.sql`.
* **Testing guide:** `docs/testing/phase10-return-refund-testing-guide.md`.

---

### Phase 4 / 14: Elasticsearch Search ✅ (Completed & E2E Verified — May 28, 2026)

* **Architecture:** Replaced `@Async ApplicationEvent` listener with **Transactional Outbox Pattern** for durable, crash-safe ES indexing.
* **`search_outbox` table:** Written in the same MySQL transaction as the product write — guarantees no lost events on crash/deploy.
* **`SearchOutboxPoller`:** `@Scheduled` every 5 seconds. Reads up to 100 `PENDING` rows, bulk-upserts to ES, marks `DONE`.
* **`ProductDocument`:** Denormalised ES document — `availableSizes[]`, `availableColors[]`, `colorHexCodes[]`, `minPrice`, `maxPrice`, `totalStock`, `avgRating`, `reviewCount` — all built from the MySQL variant matrix at index time.
* **`SearchService`:** Faceted search via ES Java Client 9.x. Relevance: `name^5, categoryName^2, tags^2, description^1` + function-score in-stock boost (1.5×). Aggregations: size terms, color terms, price stats.
* **`SearchReindexJob`:** Admin `POST /api/v1/admin/search/reindex` + nightly `@Scheduled(cron="0 0 3 * * *")` for safety net re-sync.
* **Circuit breaker:** All ES calls wrapped in try/catch — falls back to MySQL on any failure. `fallbackMode: true` in response signals frontend to show degraded mode banner.
* **Autocomplete:** `GET /api/v1/search/autocomplete?q=ho` — edge-ngram analyzer, up to 5 suggestions, min 2 chars.
* **ES Version:** Upgraded Docker container from `elasticsearch:8.13.0` to `elasticsearch:9.0.1` — required for Spring Boot 4.x / Spring Data ES 6.x / ES Java Client 9.x protocol compatibility.
* **Documentation:** `docs/integrations/elasticsearch.md` fully rewritten, `docs/testing/search-swagger-e2e-guide.md` created.

#### Bugs Found & Fixed During Phase 4/14 (May 28, 2026)

**Bug ES-1 — `pom.xml` invalid dependency IDs broke Maven resolution**
- **Symptom:** `Cannot resolve symbol 'lombok'` in IDE + Maven compile errors.
- **Root cause:** Three artifact IDs were incorrect: `spring-boot-starter-webmvc` (should be `spring-boot-starter-web`), `spring-boot-starter-data-jpa-test` (doesn't exist), etc.
- **Fix:** Corrected all three artifact IDs in `pom.xml`.

**Bug ES-2 — ES 8.13 incompatible with Spring Data ES 6.x / ES Client 9.x**
- **Symptom:** `status: 400` on every `es/indices.exists` call — empty response body, no useful error.
- **Root cause:** Spring Boot 4.x bundles ES Java Client 9.x which communicates using a protocol version ES 8.x does not support.
- **Fix:** Upgraded Docker container to `elasticsearch:9.0.1`.

**Bug ES-3 — `GET /api/v1/search` returned 401 for unauthenticated users**
- **Symptom:** Swagger returned `401 Authentication required` even though search is a public endpoint.
- **Root cause:** `/api/v1/search` and `/api/v1/search/autocomplete` were missing from `SecurityConfig.PUBLIC_MATCHERS`, causing them to hit the `anyRequest().authenticated()` catch-all.
- **Fix:** Added both paths to `PUBLIC_MATCHERS` in `SecurityConfig.java`.

**Bug ES-4 — Size filter returned 0 results despite matching products existing**
- **Symptom:** `GET /api/v1/search?sizes=L` → `totalElements: 0`. Products with size L existed.
- **Root cause:** `product_attribute_values` had inconsistent naming — Product 1 used abbreviations (`S`, `M`, `L`) while Product 2 used full words (`Small`, `Large`). ES term filter is exact and case-sensitive — `"L"` never matched `"Large"`.
- **Fix:** SQL migration normalised all size values to abbreviations. Full reindex triggered. Naming convention documented in `docs/backend/product-attribute-system.md`.

**Bug ES-5 — `return_requests.order_id` FK type mismatch**
- **Symptom:** `java.sql.SQLException: Referencing column 'order_id' and referenced column 'id' are incompatible` on every startup.
- **Root cause:** `orders.id` was created as `BIGINT UNSIGNED` (manually, before Hibernate management) but `return_requests.order_id` was `BIGINT` (signed). MySQL rejects FKs between signed and unsigned columns.
- **Fix:** `ALTER TABLE return_requests MODIFY COLUMN order_id BIGINT UNSIGNED NOT NULL` + FK constraint added directly in MySQL.

**Bug ES-6 — `CLOUDINARY_CLOUD_NAME` missing caused startup failure outside IntelliJ**
- **Symptom:** `mvnw spring-boot:run` failed with `PlaceholderResolutionException: Could not resolve placeholder 'CLOUDINARY_CLOUD_NAME'`.
- **Root cause:** Three env-var groups (Cloudinary, Razorpay, webhook secret) had no default fallback values in `application.properties`. IntelliJ passed them via run config; Maven CLI had none.
- **Fix:** Added `:default` fallback values to all 6 unguarded env vars in `application.properties`. App boots and serves all non-Cloudinary/Razorpay endpoints without the env vars set.

**Bug ES-7 — `ImageGallery.tsx` duplicate React key warning**
- **Symptom:** Browser console: `Encountered two children with the same key '2'` in `ImageGallery.tsx:62`.
- **Root cause:** `variantImages` and `galleryImages` are independent DB-issued ID sequences. Both can have `id=2`. Merging them into `allImages` with `key={img.id}` produces duplicate keys.
- **Fix:** Tagged each image with a source prefix before merging: `v-${img.id}` for variant images, `g-${img.id}` for gallery images. Used `_key` as the React `key` prop.

---

## 4. Immediate Next Tasks

> **Decision (May 29 2026):** Phase 4/14 (Elasticsearch Search) fully implemented and E2E verified.
> All known bugs resolved. Next focus is production hardening and deployment.

1. **Phase 15 — Production Deployment:** GitHub CI/CD, multi-stage Docker builds, VPS configuration, SSL/TLS.
2. **Phase 16 — Platform Hardening:** Input sanitisation, rate limiting, load testing, security headers (CSP, HSTS).
3. **Section D (Razorpay Refund Live Test):** Requires a real `pay_xxx` ID from Checkout.js in production flow.

---

## 5. Current Work & System Health
* **Database Connection:** Confirmed on port `3307` (`rawego` schema).
* **Redis:** Running in Docker (`redis:7-alpine`) on port `6379`. Cart keys verified live.
* **Server Port:** Running on standard port `8080`.
* **API Sandbox:** Interactive endpoints sandbox accessible at `/docs`.
* **Frontend:** Built with Vite React MUI TS; uses Zustand stores for state persistence and TanStack Query for remote requests.
