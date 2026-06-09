# Order Module — Complete Swagger E2E Testing Guide

> **Module:** Phase 6 — Order Module
> **Backend:** `http://localhost:8080`
> **Swagger UI:** `http://localhost:8080/docs`
> **Date:** May 24–25, 2026
> **Status:** ✅ E2E Complete — All 25 test cases passed. 6 bugs found and fixed during live testing.

---

## Section 0 — Environment Reference

Everything you need in one place before you start.

### Service URLs

| Service | URL / Connection |
|---|---|
| **Spring Boot API** | `http://localhost:8080` |
| **Swagger UI** | `http://localhost:8080/docs` |
| **MySQL** | `localhost:3307` / schema: `rawego` / user: `root` |
| **Redis CLI** | `docker exec -it redis redis-cli` |

### Test Accounts

| Role | Email | Password |
|---|---|---|
| **Admin** | `admin@ego.com` | `Admin@123` |
| **Customer** | `customer@ego.com` | `SecureCustomer@123` |

> If these accounts don't exist yet, register them first via `POST /api/v1/auth/register`.

### Notation

Throughout this guide:
- `{AT}` = your current access token
- `{orderId}` = the order ID returned from checkout
- `{variantId}` = a real variant ID from your database
- Values you must fill in are marked with `← FILL IN`

---

## Section 1 — Pre-Flight Checks

### 1.1 Start Services

```powershell
# Terminal 1 — Spring Boot
cd raw-ego
.\mvnw spring-boot:run

# Terminal 2 — Redis (if not running)
docker start redis

# Verify Redis is up
docker exec -it redis redis-cli ping
# Expected: PONG
```

Wait for: `Started RawEgoApplication in X.XXX seconds`

### 1.2 Apply the Database Schema

Run the order module DDL **once** before testing:

```sql
-- Connect: localhost:3307 | schema: rawego | user: root
-- Run the full contents of: docs/database/schema_order_module.sql

-- Verify tables were created:
SHOW TABLES LIKE 'order%';
```

**Expected output:**
```
+-------------------------------+
| Tables_in_rawego (order%)     |
+-------------------------------+
| order_items                   |
| order_status_history          |
| orders                        |
+-------------------------------+
3 rows in set
```

### 1.3 Verify Inventory Exists

You need at least one active variant with stock to test checkout.

```sql
-- Find a variant with available stock
SELECT
    pv.id AS variant_id,
    pv.sku,
    p.name AS product_name,
    ir.quantity_available,
    ir.quantity_reserved
FROM product_variants pv
JOIN products p ON p.id = pv.product_id
JOIN inventory_records ir ON ir.variant_id = pv.id
WHERE pv.is_active = 1
  AND ir.quantity_available > 2
LIMIT 5;
```

**Record a variant to use throughout this guide:**

| Field | Your value |
|---|---|
| `variantId` | `___` ← FILL IN |
| `sku` | `___` ← FILL IN |
| `product_name` | `___` ← FILL IN |
| `quantity_available` (baseline) | `___` ← FILL IN |

> If no active variants exist, create one via the Admin Catalog endpoints first, or set stock via `PUT /api/v1/admin/inventory/{variantId}`.

---

## Section 2 — Authentication Setup

### 2.1 Login as Customer

In Swagger UI → `POST /api/v1/auth/login` → **Try it out**:

```json
{
  "email": "customer@ego.com",
  "password": "SecureCustomer@123"
}
```

**Expected response (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "550e8400-...",
    "user": { "id": 2, "email": "customer@ego.com", "role": "CUSTOMER" }
  }
}
```

Copy the `accessToken`. Click 🔒 **Authorize** → paste token → **Authorize**.

**Record:**
| Field | Value |
|---|---|
| Customer access token | `eyJ...` ← FILL IN |
| Customer user ID | `___` ← FILL IN (from `data.user.id`) |

### 2.2 Login as Admin (keep this token for later)

Repeat with admin credentials — copy the admin `accessToken` separately.

```json
{
  "email": "admin@ego.com",
  "password": "SecureAdmin@123"
}
```

You'll need to swap between tokens during testing. In Swagger, click 🔒 **Authorize** and replace the token each time you switch roles.

---

## Section 3 — Seed the Cart

Before checking out, you need items in the cart.

> **Auth:** Switch Swagger to **Customer token** for all steps in this section.

### 3.1 Add Item to Cart

`POST /api/v1/cart/add` → **Try it out**:

```json
{
  "variantId": ← YOUR variantId HERE,
  "quantity": 2
}
```

**Expected (200 OK):**
```json
{
  "success": true,
  "message": "Item added to cart.",
  "data": {
    "items": [
      {
        "variantId": 1,
        "sku": "EGO-MEN-0001-BLK-M",
        "productName": "Oversized Acid-Wash Tee",
        "variantLabel": "Black / M",
        "price": 1299.00,
        "primaryImageUrl": "https://res.cloudinary.com/...",
        "quantity": 2,
        "stockStatus": "IN_STOCK",
        "quantityAvailable": 98
      }
    ],
    "itemCount": 2,
    "subtotal": 2598.00
  }
}
```

**Verify (Redis CLI):**
```bash
docker exec -it redis redis-cli HGETALL cart:{your_customer_id}
# Expected: "1" "2"  (variantId → quantity)

docker exec -it redis redis-cli TTL cart:{your_customer_id}
# Expected: ~604800 (7 days)
```

**Record the subtotal:** `___` ← FILL IN (e.g., `2598.00`)

### 3.2 Confirm Cart Is Ready

`GET /api/v1/cart`

**Expected:** Same cart response. `itemCount: 2`, `subtotal: 2598.00`.

---

## Section 4 — Test Block A: Happy Path Checkout

### TEST A-1: Place Order ✅ Happy Path

`POST /api/v1/orders/checkout` → **Try it out**:

```json
{
  "shippingAddress": "123 MG Road, Bengaluru, Karnataka 560001"
}
```

**Expected (201 Created):**
```json
{
  "success": true,
  "message": "Order placed successfully.",
  "data": {
    "id": 1,
    "status": "PENDING_PAYMENT",
    "subtotal": 2598.00,
    "shippingTotal": 0.00,
    "grandTotal": 2598.00,
    "shippingAddress": "123 MG Road, Bengaluru, Karnataka 560001",
    "items": [
      {
        "id": 1,
        "variantId": 1,
        "skuSnapshot": "EGO-MEN-0001-BLK-M",
        "productNameSnapshot": "Oversized Acid-Wash Tee",
        "variantLabelSnapshot": "Black / M",
        "primaryImageUrlSnapshot": "https://res.cloudinary.com/...",
        "unitPrice": 1299.00,
        "quantity": 2,
        "lineTotal": 2598.00
      }
    ],
    "statusHistory": [
      {
        "status": "PENDING_PAYMENT",
        "note": "Order placed by customer.",
        "createdAt": "2026-05-24T..."
      }
    ],
    "createdAt": "2026-05-24T...",
    "updatedAt": "2026-05-24T..."
  }
}
```

**Checklist:**
- [ ] HTTP status **201 Created**
- [ ] `status` = `PENDING_PAYMENT`
- [ ] `grandTotal` matches the cart subtotal you recorded (e.g., `2598.00`)
- [ ] `shippingTotal` = `0.00`
- [ ] `items[0].skuSnapshot` = `"EGO-MEN-0001-BLK-M"` (not a live join — frozen value)
- [ ] `items[0].primaryImageUrlSnapshot` = a Cloudinary URL (preserved image at checkout)
- [ ] `statusHistory` has exactly **1 entry** with `status: PENDING_PAYMENT`

**Record the order ID:** `{orderId}` = `___` ← FILL IN (e.g., `1`)

---

### TEST A-2: Verify Cart Was Cleared ✅

`GET /api/v1/cart`

**Expected (200 OK):**
```json
{
  "success": true,
  "data": {
    "items": [],
    "itemCount": 0,
    "subtotal": 0
  }
}
```

**Redis verification:**
```bash
docker exec -it redis redis-cli EXISTS cart:{your_customer_id}
# Expected: (integer) 0  ← key is GONE
```

- [ ] Cart response is empty
- [ ] Redis key no longer exists

---

### TEST A-3: Verify Inventory Was Committed ✅

```sql
SELECT
    variant_id,
    quantity_available,
    quantity_reserved
FROM inventory_records
WHERE variant_id = ← YOUR variantId;
```

**Expected:**
- `quantity_available` = baseline − 2 (e.g., `98` if baseline was `100`)
- `quantity_reserved` = `0` (released by commit — no longer held)

- [ ] `quantity_available` decremented by 2
- [ ] `quantity_reserved` = 0

---

### TEST A-4: Verify MySQL Order Rows ✅

```sql
-- The order itself
SELECT
    id,
    user_id,
    status,
    subtotal,
    shipping_total,
    grand_total,
    LEFT(shipping_address, 50) AS shipping_address,
    created_at
FROM orders
WHERE id = 1;

-- The line items (all snapshot fields must be populated)
SELECT
    id,
    order_id,
    variant_id,
    sku_snapshot,
    product_name_snapshot,
    variant_label_snapshot,
    LEFT(primary_image_url_snapshot, 60) AS image_url_preview,
    unit_price_snapshot,
    quantity,
    line_total
FROM order_items
WHERE order_id = 1;

-- The status history (should have exactly 1 row)
SELECT
    id,
    order_id,
    status,
    note,
    created_at
FROM order_status_history
WHERE order_id = 1;
```

**Expected:**
- `orders`: 1 row, `status = PENDING_PAYMENT`, `grand_total = 2598.00`
- `order_items`: 1 row, all 5 snapshot columns populated (SKU, product name, variant label, image URL, unit price)
- `order_status_history`: 1 row, `status = PENDING_PAYMENT`, `note = "Order placed by customer."`

- [ ] All 3 tables have expected rows
- [ ] `primary_image_url_snapshot` is NOT NULL

---

## Section 5 — Test Block B: Order Read Endpoints

### TEST B-1: Get Order List ✅

`GET /api/v1/orders` (no params needed — defaults to page 0, size 10)
{
  "page": 0,
  "size": 10,
  "sort": ""
}

**Expected (200 OK):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "status": "PENDING_PAYMENT",
        "grandTotal": 2598.00,
        "itemCount": 2,
        "createdAt": "2026-05-24T..."
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

- [ ] HTTP 200
- [ ] `content` contains the order just placed
- [ ] `itemCount` = 2 (total units, not distinct items)

---

### TEST B-2: Get Order Detail ✅

`GET /api/v1/orders/{orderId}` → set `orderId` = `1`

**Expected (200 OK):** Full `OrderDetailResponse` — same shape as the checkout response in A-1.

- [ ] `items` array present with all snapshot fields
- [ ] `statusHistory` has 1 entry
- [ ] `shippingAddress` = `"123 MG Road, Bengaluru, Karnataka 560001"`

---

### TEST B-3: Pagination ✅

Place a second order first (add to cart → checkout again with a different address), then:

`GET /api/v1/orders?page=0&size=1`

**Expected:**
```json
{
  "data": {
    "content": [ { "id": 2, ... } ],
    "totalElements": 2,
    "totalPages": 2,
    "first": true,
    "last": false
  }
}
```

`GET /api/v1/orders?page=1&size=1`

**Expected:** `content: [{ "id": 1, ... }]`, `last: true`

- [ ] Newest order appears first on page 0
- [ ] `totalElements` = 2

---

## Section 6 — Test Block C: Order Cancellation

### TEST C-1: Cancel a PENDING_PAYMENT Order ✅ Happy Path

> Use `orderId = 1` (the first order you placed — still PENDING_PAYMENT).

`POST /api/v1/orders/1/cancel` → **Try it out** → **Execute** (no request body needed)

**Expected (200 OK):**
```json
{
  "success": true,
  "message": "Order cancelled successfully.",
  "data": {
    "id": 1,
    "status": "CANCELLED",
    "items": [ ... ],
    "statusHistory": [
      { "status": "PENDING_PAYMENT", "note": "Order placed by customer.", "createdAt": "..." },
      { "status": "CANCELLED",       "note": "Cancelled by customer.",    "createdAt": "..." }
    ]
  }
}
```

- [ ] HTTP 200
- [ ] `status` = `CANCELLED`
- [ ] `statusHistory` has **2 entries** — first `PENDING_PAYMENT`, then `CANCELLED`

---

### TEST C-2: Verify Inventory Was Restored ✅

```sql
SELECT quantity_available, quantity_reserved
FROM inventory_records
WHERE variant_id = ← YOUR variantId;
```

**Expected:** `quantity_available` is back to the **original baseline** (before the checkout in A-1).

```bash
# Also verify in Spring Boot console:
# INFO  ...InventoryReservationService : Inventory restored: variantId=1 units=+2
```

- [ ] `quantity_available` restored to original value
- [ ] Console log shows `Inventory restored`

---

### TEST C-3: Verify MySQL Status History ✅

```sql
SELECT status, note, created_at
FROM order_status_history
WHERE order_id = 1
ORDER BY created_at ASC;
```

**Expected:** 2 rows:
```
PENDING_PAYMENT | Order placed by customer. | 2026-05-24 ...
CANCELLED       | Cancelled by customer.    | 2026-05-24 ...
```

- [ ] Exactly 2 history rows
- [ ] Timestamps are chronologically ordered

---

## Section 7 — Test Block D: Admin Order Management

> **Switch Swagger to the Admin token now.**
> Click 🔒 **Authorize** → replace token with the admin `accessToken`.

### TEST D-1: Place a Fresh Order (as Customer) for Admin Testing

Switch back to Customer token → add items to cart → checkout → record the new `{orderId}`.

Then switch back to Admin token for the rest of this section.

**New order ID for admin testing:** `{adminTestOrderId}` = `___` ← FILL IN

---

### TEST D-2: Admin List All Orders ✅

`GET /api/v1/admin/orders`

**Expected (200 OK):** Paginated list of ALL orders from all customers.

- [ ] Both customer orders visible
- [ ] HTTP 200

---

### TEST D-3: Admin Filter by Status ✅

`GET /api/v1/admin/orders?status=PENDING_PAYMENT`

**Expected:** Only `PENDING_PAYMENT` orders returned.

`GET /api/v1/admin/orders?status=CANCELLED`

**Expected:** Only the cancelled order from Section 6 returned.

- [ ] Status filter works correctly

---

### TEST D-4: Advance Status — CONFIRMED ✅

`PUT /api/v1/admin/orders/{adminTestOrderId}/status`

```json
{
  "status": "CONFIRMED",
  "note": "Payment received via bank transfer. Ref: IMPS20260524"
}
```

**Expected (200 OK):**
```json
{
  "success": true,
  "message": "Order status updated.",
  "data": {
    "id": 2,
    "status": "CONFIRMED",
    "statusHistory": [
      { "status": "PENDING_PAYMENT", "note": "Order placed by customer.",                     "createdAt": "..." },
      { "status": "CONFIRMED",       "note": "Payment received via bank transfer. Ref: ...", "createdAt": "..." }
    ]
  }
}
```

- [ ] `status` = `CONFIRMED`
- [ ] `statusHistory` has 2 entries
- [ ] Admin note is preserved in history

---

### TEST D-5: Advance Status — PROCESSING ✅

`PUT /api/v1/admin/orders/{adminTestOrderId}/status`

```json
{
  "status": "PROCESSING",
  "note": "Packed and assigned to picker — Shelf B7"
}
```

**Expected:** `status = PROCESSING`, 3 history entries.

- [ ] HTTP 200
- [ ] `status` = `PROCESSING`

---

### TEST D-6: Advance Status — SHIPPED ✅

`PUT /api/v1/admin/orders/{adminTestOrderId}/status`

```json
{
  "status": "SHIPPED",
  "note": "Handed to Delhivery. AWB: 1234567890"
}
```

**Expected:** `status = SHIPPED`, 4 history entries.

---

### TEST D-7: Advance Status — DELIVERED ✅ (Terminal)

`PUT /api/v1/admin/orders/{adminTestOrderId}/status`

```json
{
  "status": "DELIVERED",
  "note": "Delivered and signed by customer."
}
```

**Expected:** `status = DELIVERED`, 5 history entries.

- [ ] `status` = `DELIVERED`

**Full status history at this point (verify via MySQL):**
```sql
SELECT status, note, created_at
FROM order_status_history
WHERE order_id = {adminTestOrderId}
ORDER BY created_at ASC;
```

```
PENDING_PAYMENT | Order placed by customer.                        | ...
CONFIRMED       | Payment received via bank transfer. Ref: ...     | ...
PROCESSING      | Packed and assigned to picker — Shelf B7         | ...
SHIPPED         | Handed to Delhivery. AWB: 1234567890             | ...
DELIVERED       | Delivered and signed by customer.                | ...
```

---

## Section 8 — Test Block E: Negative / Error Cases

All expected failures. Every one must return the exact HTTP status and `success: false`.

---

### TEST E-1: Checkout with Empty Cart ❌ Expect 409

Clear the cart first (`DELETE /api/v1/cart`), then attempt checkout:

`POST /api/v1/orders/checkout`
```json
{ "shippingAddress": "123 MG Road, Bengaluru" }
```

**Expected (409 Conflict):**
```json
{
  "success": false,
  "message": "Cannot checkout: your cart is empty."
}
```

- [ ] HTTP 409
- [ ] Correct error message

---

### TEST E-2: Checkout with Missing Shipping Address ❌ Expect 400

`POST /api/v1/orders/checkout`
```json
{ "shippingAddress": "" }
```

**Expected (400 Bad Request):**
```json
{
  "success": false,
  "message": "Validation failed. Please check the request and try again.",
  "errors": [
    { "field": "shippingAddress", "message": "Shipping address is required." }
  ]
}
```

- [ ] HTTP 400
- [ ] `errors` array with `field: shippingAddress`

---

### TEST E-3: Checkout with No Body ❌ Expect 400

`POST /api/v1/orders/checkout` — send with completely empty body `{}`

**Expected (400 Bad Request):** Same validation error as E-2.

---

### TEST E-4: Cancel a CONFIRMED Order ❌ Expect 409

> Use `{adminTestOrderId}` (the order you advanced to DELIVERED in D-7, or create a new one and advance to CONFIRMED).

Place a new order, confirm it via admin, then switch to Customer token and try:

`POST /api/v1/orders/{confirmedOrderId}/cancel`

**Expected (409 Conflict):**
```json
{
  "success": false,
  "message": "Only PENDING_PAYMENT orders can be cancelled. Current status: CONFIRMED"
}
```

- [ ] HTTP 409
- [ ] Error message includes the current status

---

### TEST E-5: Get Another User's Order ❌ Expect 404

> Log in as a second customer (or register one). Try to GET an order that belongs to the first customer.

With Customer 2's token:
`GET /api/v1/orders/1`

**Expected (404 Not Found):**
```json
{
  "success": false,
  "message": "Order not found: id=1"
}
```

> **Why 404 instead of 403?** Returning 403 would confirm the order exists — that leaks order IDs. 404 tells the attacker nothing.

- [ ] HTTP 404 (not 403)

---

### TEST E-6: Get Non-Existent Order ❌ Expect 404

`GET /api/v1/orders/99999`

**Expected (404 Not Found):**
```json
{
  "success": false,
  "message": "Order not found: id=99999"
}
```

- [ ] HTTP 404

---

### TEST E-7: Access Orders Without Authentication ❌ Expect 401

Click 🔓 **Authorize → Logout** in Swagger (clear the token), then:

`GET /api/v1/orders`

**Expected (401 Unauthorized):**
```json
{
  "success": false,
  "message": "Authentication failed."
}
```

`POST /api/v1/orders/checkout` (with body)

**Expected (401 Unauthorized):** Same.

- [ ] HTTP 401 for both

---

### TEST E-8: Customer Accessing Admin Endpoints ❌ Expect 403

Switch back to **Customer token**:

`GET /api/v1/admin/orders`

**Expected (403 Forbidden):**
```json
{
  "success": false,
  "message": "Access denied"
}
```

`PUT /api/v1/admin/orders/1/status`
```json
{ "status": "CONFIRMED" }
```

**Expected (403 Forbidden):** Same.

- [ ] HTTP 403 for both admin endpoints

---

### TEST E-9: Invalid Admin Status Transition ❌ Expect 400

Place a fresh order, advance it to `DELIVERED` (follow D-4 through D-7 quickly). Then try to go backward:

`PUT /api/v1/admin/orders/{deliveredOrderId}/status`
```json
{ "status": "CONFIRMED" }
```

**Expected (400 Bad Request):**
```json
{
  "success": false,
  "message": "Invalid status transition: DELIVERED → CONFIRMED. Allowed transitions: CONFIRMED→PROCESSING|CANCELLED, PROCESSING→SHIPPED, SHIPPED→DELIVERED."
}
```

Also test:
```json
{ "status": "PENDING_PAYMENT" }   ← also invalid
{ "status": "REFUNDED" }           ← also invalid (Phase 10 only)
```

- [ ] HTTP 400 for all backward/invalid transitions

---

### TEST E-10: Admin Update Non-Existent Order ❌ Expect 404

`PUT /api/v1/admin/orders/99999/status`
```json
{ "status": "CONFIRMED" }
```

**Expected (404 Not Found):**
```json
{
  "success": false,
  "message": "Order not found: id=99999"
}
```

- [ ] HTTP 404

---

### TEST E-11: Cancel Another User's Order ❌ Expect 404

With Customer 2's token, try to cancel Customer 1's PENDING_PAYMENT order:

`POST /api/v1/orders/1/cancel`

**Expected (404 Not Found):**
```json
{
  "success": false,
  "message": "Order not found: id=1"
}
```

- [ ] HTTP 404 (not 403 — same anti-enumeration principle as E-5)

---

### TEST E-12: Invalid Status Value in Admin Request ❌ Expect 400

`PUT /api/v1/admin/orders/1/status`
```json
{ "status": "SHIPPED_AND_DELIVERED" }
```

**Expected (400 Bad Request):** Jackson deserialization failure or validation error.

- [ ] HTTP 400

---

## Section 9 — Console Log Verification

After running the happy-path tests, verify the Spring Boot console shows structured logs.

### Checkout success
```
INFO  OrderService : Order placed: orderId=1 userId=2 grandTotal=2598.00
INFO  InventoryReservationService : Inventory committed: variantId=1 units=2
```

### Cancellation with inventory restore
```
INFO  OrderService : Order cancelled: orderId=1 userId=2
INFO  InventoryReservationService : Inventory restored: variantId=1 units=+2
```

### Admin status advancement
```
INFO  OrderService : Order status advanced: orderId=2 PENDING_PAYMENT → CONFIRMED
INFO  OrderService : Order status advanced: orderId=2 CONFIRMED → PROCESSING
```

### Validation failures (expected WARNs — not ERRORs)
```
WARN  GlobalExceptionHandler : Bad request — conflict: Cannot checkout: your cart is empty.
WARN  GlobalExceptionHandler : Bad request — conflict: Only PENDING_PAYMENT orders can be cancelled. Current status: CONFIRMED
WARN  GlobalExceptionHandler : Bad request — illegal argument: Invalid status transition: DELIVERED → CONFIRMED...
```

---

## Section 10 — Final Database State Verification

Run this after completing all tests to confirm the final state of the order tables:

```sql
-- Order count by status
SELECT status, COUNT(*) AS order_count
FROM orders
GROUP BY status
ORDER BY order_count DESC;

-- Status history row count per order (should match transition count)
SELECT
    o.id AS order_id,
    o.status AS current_status,
    COUNT(osh.id) AS history_entries
FROM orders o
LEFT JOIN order_status_history osh ON osh.order_id = o.id
GROUP BY o.id, o.status
ORDER BY o.id;

-- Confirm all snapshot fields are populated (no NULLs on required columns)
SELECT
    id,
    sku_snapshot,
    product_name_snapshot,
    variant_label_snapshot,
    unit_price_snapshot,
    quantity,
    line_total,
    CASE WHEN primary_image_url_snapshot IS NULL THEN '⚠ NULL' ELSE '✓ OK' END AS image_snapshot
FROM order_items
ORDER BY id;

-- Confirm inventory integrity — no negative quantities
SELECT variant_id, quantity_available, quantity_reserved
FROM inventory_records
WHERE quantity_available < 0 OR quantity_reserved < 0;
-- Expected: 0 rows (empty result)
```

---

## Section 11 — Test Checklist Summary

Mark each test as ✅ pass or ❌ fail before closing the session.

### Section A — Happy Path Checkout
- [ ] A-1: `POST /checkout` → 201, full `OrderDetailResponse` with all snapshot fields
- [ ] A-2: Cart cleared after checkout — `GET /cart` returns empty
- [ ] A-3: Redis cart key deleted — `EXISTS cart:{userId}` = 0
- [ ] A-4: `quantity_available` decremented by 2 in MySQL
- [ ] A-4: `quantity_reserved` = 0 after commit

### Section B — Order Read Endpoints
- [ ] B-1: `GET /orders` → paginated list, contains the new order
- [ ] B-2: `GET /orders/{id}` → full detail with items and status history
- [ ] B-3: Pagination — newest first, correct `totalElements`

### Section C — Cancellation
- [ ] C-1: `POST /orders/{id}/cancel` → 200, status = CANCELLED, 2 history entries
- [ ] C-2: `quantity_available` restored to original baseline
- [ ] C-3: MySQL `order_status_history` has 2 rows, chronologically ordered

### Section D — Admin
- [ ] D-2: `GET /admin/orders` → all orders visible to admin
- [ ] D-3: Status filter (`?status=PENDING_PAYMENT`) works correctly
- [ ] D-4: CONFIRMED → 200, admin note in history
- [ ] D-5: PROCESSING → 200
- [ ] D-6: SHIPPED → 200
- [ ] D-7: DELIVERED → 200, full 5-entry status history

### Section E — Error Cases
- [ ] E-1: Empty cart checkout → 409
- [ ] E-2: Blank shipping address → 400 with `errors` array
- [ ] E-4: Cancel CONFIRMED order → 409
- [ ] E-5: Access other user's order → 404 (not 403)
- [ ] E-6: Non-existent order → 404
- [ ] E-7: No auth → 401
- [ ] E-8: Customer hitting admin endpoints → 403
- [ ] E-9: Invalid status transition → 400
- [ ] E-10: Admin update non-existent order → 404
- [ ] E-11: Cancel another user's order → 404
- [ ] E-12: Invalid status enum value → 400

---

> **All 25 checks passing = Phase 6 E2E verified ✅**
> Next phase: Phase 7 — Razorpay Integration
