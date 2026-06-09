# TODO Next Phase — EGO Platform Onboarding

This document guides developers and AI agents on the immediate next steps, feature dependencies, and critical warnings for the upcoming development phases.

> **Last updated: May 28, 2026 — Phase 13 (Admin Dashboard Client) is complete.**
> Next targets: Phase 15 (Production Deployment) and Phase 16 (Platform Hardening).

---

## 1. Primary Target: Phase 11 — Administrative Portal

### Key Objective
Build the full admin dashboard frontend: KPI metrics, order management, return management, and user management panels. The backend for all these domains already exists — this phase is pure frontend work.

### Scope

#### Dashboard KPIs
- Total revenue (sum of `orders.grand_total` where status ≠ CANCELLED/PENDING_PAYMENT)
- Orders by status (count per `OrderStatus` value)
- Low stock alerts (variants where `quantity_available ≤ low_stock_threshold`)
- Return requests pending review (count where `return_requests.status = 'REQUESTED'`)
- Recent orders table (last 10 orders)

#### Admin Panels (Frontend Pages)
1. **Order Management Panel** — list, filter by status, view detail, advance status (existing backend: `GET/PUT /api/v1/admin/orders`)
2. **Return Management Panel** — list, filter by status, approve/reject (existing backend: `GET/PUT /api/v1/admin/returns`)
3. **Coupon Management Panel** — CRUD (existing backend: `GET/POST/PUT/DELETE /api/v1/admin/coupons`)
4. **Review Moderation Panel** — list and delete reviews (existing backend: `DELETE /api/v1/admin/reviews/{id}`)

### Implementation Notes
- The Admin Catalog UI is already built (Phase 11 partially complete — see CURRENT_STATUS.md)
- All backend APIs exist — this is frontend-only work
- Use existing `useOrders.ts` + `useReturns.ts` + `useOrders.ts` hooks (already implemented)

---

## 2. Secondary Target: Phase 12 — Consumer Storefront

### Key Objective
Build the customer-facing storefront pages using the existing React frontend infrastructure.

### Scope

#### Pages to Build
1. **Product Listing Page (PLP)** — grid of ACTIVE products, use existing `GET /api/v1/products`
2. **Product Detail Page (PDP)** — variant selector (Color × Size matrix), image gallery, add to cart
3. **Cart Sidebar / Drawer** — live cart state using existing `useCart.ts` hooks
4. **Checkout Flow** — shipping address form, coupon code input, order summary, payment
5. **Order History Page** — list of customer orders using `useOrders.ts`
6. **Order Detail Page** — full order with status history, return request button (if DELIVERED)
7. **Return Request Flow** — modal/page using `useReturns.ts` hooks (already implemented)

### Implementation Notes
- All backend APIs are ready (Phases 1–10 complete)
- All TanStack Query hooks exist — PLP/PDP just need UI components
- Razorpay Checkout.js hook (`useRazorpay.ts`) already implemented in Phase 7
- Cart merge on login already wired in `useAuth.ts`

---

## 3. Critical Warnings & Guardrails

> [!CAUTION]
> **Never set `order.status = REFUNDED` through the admin status endpoint.** The `REFUNDED` state is exclusively managed by `ReturnService.completeRefundAndUpdateOrder()` (Phase 10). The admin `assertValidAdminTransition()` guard in `OrderStatus.java` blocks this correctly.

> [!WARNING]
> **Return window is 7 days from `order.updatedAt`** — the timestamp when the order was last modified (set to DELIVERED). Do not use `createdAt`. This is enforced in `ReturnService.initiateReturn()`.

> [!IMPORTANT]
> **Partial refunds are supported.** The admin can specify any `refundAmount` between 0.01 and `order.grandTotal`. The system does NOT enforce full-refund-only. This is intentional for cases like partial item returns.

> [!WARNING]
> **Cash orders cannot be refunded via Razorpay.** Orders where `razorpay_payment_id IS NULL` will receive a 409 on the approve path. Admin must handle COD refunds manually (bank transfer, store credit — out of scope for Phase 10).


### Key Objective
Build the checkout pipeline that converts a confirmed cart into a persisted Order. This is the central transaction of the commerce flow: it calls `InventoryReservationService.commit()` to atomically deduct stock, persists order rows, and clears the cart.

### Domain Model

#### Entities to Create
```
orders
  id, user_id (FK users), status, subtotal, shipping_total, grand_total,
  shipping_address (JSON snapshot), created_at, updated_at, version

order_items
  id, order_id (FK orders), variant_id (FK product_variants),
  sku_snapshot, product_name_snapshot, variant_label_snapshot,
  unit_price_snapshot, quantity, line_total

order_status_history
  id, order_id (FK orders), status, note, created_at
```

> [!IMPORTANT]
> **Price Snapshots (LOCKED):** `unit_price_snapshot` in `order_items` must capture the variant's price **at checkout time** — never the live price. This prevents price changes from retroactively altering historical orders. The `sku_snapshot`, `product_name_snapshot`, and `variant_label_snapshot` fields exist for the same reason.

#### Order Status Lifecycle (State Machine)
```
PENDING_PAYMENT
    │
    ├──(payment confirmed / Phase 7)──► CONFIRMED
    │                                        │
    │                                        ├──► PROCESSING
    │                                        │         │
    │                                        │         └──► SHIPPED ──► DELIVERED
    │                                        │
    │                                        └──► CANCELLED
    │
    └──(payment timeout / failure)──► CANCELLED
```

### Recommended Implementation Order

#### Step 1: Backend Entities & Repository
1. Create `Order.java`, `OrderItem.java`, `OrderStatusHistory.java` JPA entities in `com.ego.raw_ego.order.entity`.
2. Create corresponding repositories: `OrderRepository`, `OrderItemRepository`.
3. `OrderStatus` enum: `PENDING_PAYMENT`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `REFUNDED`.

#### Step 2: Checkout Service
Create `OrderService.java` with a `@Transactional` `checkout(Long userId)` method:
1. Load the user's cart via `CartService.getCart(userId)`.
2. Validate cart is not empty and all items are `IN_STOCK` or `LOW_STOCK`.
3. For each cart item: call `InventoryReservationService.commit(variantId, quantity)`.
4. Persist `Order` + `OrderItem` rows with price snapshots.
5. Call `CartService.clearCart(userId)` — cart deleted after successful order.
6. Persist initial `OrderStatusHistory` record (`PENDING_PAYMENT`).
7. Return `OrderResponse`.

> [!CAUTION]
> The `commit()` + `clearCart()` calls must occur inside the **same `@Transactional` boundary**. If `clearCart()` fails (Redis error), the transaction rolls back and inventory is un-committed. Never split these into separate methods with separate transactions.

#### Step 3: Order Controller
REST endpoints under `/api/v1/orders`:
```
POST   /api/v1/orders/checkout           → place order (auth required)
GET    /api/v1/orders                    → user's own order list (paginated)
GET    /api/v1/orders/{orderId}          → order detail (owner or admin)
POST   /api/v1/orders/{orderId}/cancel   → cancel PENDING_PAYMENT order only
```

Admin endpoints under `/api/v1/admin/orders`:
```
GET    /api/v1/admin/orders              → all orders, filterable by status
PUT    /api/v1/admin/orders/{orderId}/status → advance status (CONFIRMED → PROCESSING etc.)
```

#### Step 4: Response DTOs
```
OrderSummaryResponse   → id, status, grandTotal, itemCount, createdAt
OrderDetailResponse    → full order with items[], statusHistory[]
OrderItemResponse      → skuSnapshot, productNameSnapshot, variantLabelSnapshot, unitPrice, quantity, lineTotal
```

#### Step 5: Frontend Order Hooks
New file `src/features/orders/hooks/useOrders.ts`:
```typescript
useCheckout()       → useMutation — POST /orders/checkout, clears cart on success
useOrders()         → useQuery — GET /orders (user history list)
useOrder(orderId)   → useQuery — GET /orders/{id} (detail page)
useCancelOrder()    → useMutation — POST /orders/{id}/cancel
```

---

## 2. Secondary Target: Phase 7 — Razorpay Integration

### Key Objective
Wire Razorpay into the checkout flow. The `checkout()` operation (Phase 6) creates the order in `PENDING_PAYMENT` status. Razorpay integration moves the order to `CONFIRMED` after payment.

### Implementation Pattern
1. **Razorpay Orders API:** On checkout, call `Razorpay.orders.create({ amount, currency, receipt })` and return the `razorpay_order_id` to the frontend.
2. **Frontend Payment Modal:** Use the Razorpay Checkout.js SDK to open the payment modal with `razorpay_order_id`.
3. **Webhook verification:** `POST /webhooks/razorpay` — verify HMAC signature, advance order to `CONFIRMED`, trigger notification (Phase 8).
4. **Idempotency:** Use `razorpay_order_id` as the idempotency key. Webhook may fire more than once — `ORDER ALREADY CONFIRMED` check prevents double-processing.

---

## 3. Critical Warnings & Guardrails

> [!CAUTION]
> **Inventory commit atomicity:** `InventoryReservationService.commit()` must be called inside the same `@Transactional` block as the `Order` save. A commit with no matching Order row leaves inventory permanently decremented. Always wrap both operations together.

> [!WARNING]
> **Price snapshot immutability:** Once an `OrderItem` is persisted, `unit_price_snapshot` must never be updated — not even by admin. All price mutation must go through a separate refund flow (Phase 10).

> [!IMPORTANT]
> **Order ownership enforcement:** `GET /api/v1/orders/{orderId}` must verify that `order.userId == authenticatedUserId` before returning data. Admins bypass this check via `hasRole('ADMIN')`. Never expose another user's orders through the customer endpoint.

> [!WARNING]
> **Cart state after failed checkout:** If `commit()` throws a `ConflictException` (stock exhausted during checkout race), the `@Transactional` rollback ensures `quantity_reserved` is not decremented. The cart in Redis remains unchanged. The user should see a `409` "Item sold out" error and be redirected to re-view their cart.
