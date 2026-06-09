# ORDER TRACKING BACKEND AUDIT

**Date:** 2026-06-02  
**Auditor:** Antigravity  
**Source of Truth:** Source code (entity, DTOs, service, controller, frontend types)

---

## 1. Existing Capabilities

### ✅ Order Status (Current State)
**Status:** Fully implemented.

- `Order.status` (`OrderStatus` enum) is persisted in the `orders` table.
- Exposed in both `OrderSummaryResponse` and `OrderDetailResponse`.
- Enum values: `PENDING_PAYMENT → CONFIRMED → PROCESSING → SHIPPED → DELIVERED` plus `CANCELLED`, `REFUNDED`.
- This precisely maps to the desired tracking stages.

| Desired UX Step    | Enum Value       | Exists? |
|--------------------|------------------|---------|
| Placed             | `PENDING_PAYMENT` / `CONFIRMED` | ✅ Yes |
| Packed             | `PROCESSING`     | ✅ Yes |
| Shipped            | `SHIPPED`        | ✅ Yes |
| Out For Delivery   | *(none)*         | ❌ No  |
| Delivered          | `DELIVERED`      | ✅ Yes |

> **Gap:** There is no `OUT_FOR_DELIVERY` status in the enum. This is a minor UX gap — see Section 3.

---

### ✅ Status History (Timeline)
**Status:** Fully implemented.

- `OrderStatusHistory` entity exists as an append-only audit table (`order_status_history`).
- Every status transition appends a new row — never updates or deletes.
- `OrderStatusHistory` fields:
  - `status` — the status value at that point
  - `note` — optional admin or system note (String)
  - `createdAt` — Hibernate `@CreationTimestamp` — accurate transition timestamp

- `OrderDetailResponse` includes `statusHistory: List<OrderStatusHistoryResponse>`.
- `OrderStatusHistoryResponse` exposes: `status`, `note`, `createdAt`.
- Frontend `order.types.ts` already has `OrderStatusHistory` typed and `CustomerOrderDetailPage.tsx` already renders a visual timeline from `statusHistory`.

---

### ✅ Status Timestamps
**Status:** Fully implemented via `OrderStatusHistory.createdAt`.

Each row in `order_status_history` carries a Hibernate-managed `Instant createdAt`. This gives precise per-transition timestamps without any additional DB columns needed.

---

### ❌ Tracking Number
**Status:** NOT implemented.

- No `trackingNumber` field in `Order.java`.
- No `trackingNumber` field in any DTO.
- Not stored in `order_status_history` either.

---

### ❌ Courier Name
**Status:** NOT implemented.

- No `courierName` field in `Order.java`.
- Not stored anywhere in the current schema.

---

### ❌ Tracking URL
**Status:** NOT implemented.

- No `trackingUrl` field in `Order.java` or any DTO.

---

### ❌ Estimated Delivery Date
**Status:** NOT implemented.

- No `estimatedDeliveryDate` field anywhere in the schema.

---

### ❌ Actual Delivery Date
**Status:** Partially implicit.

- There is no dedicated `deliveredAt` field in `Order.java`.
- However, the timestamp of the `DELIVERED` entry in `statusHistory` functionally provides the delivery timestamp. A frontend can derive this by finding the `status === 'DELIVERED'` entry in `statusHistory`.
- This is not a first-class field, but it is derivable without a schema change.

---

### ❌ Shipment Creation Date
**Status:** Partially implicit.

- No dedicated `shippedAt` field.
- Same approach: the `SHIPPED` entry in `statusHistory` carries its own `createdAt`, which is the shipment timestamp.

---

### ✅ Order Timeline Events
**Status:** Fully implemented.

- `statusHistory` is the timeline.
- Displayed in `CustomerOrderDetailPage.tsx` (lines 353–387) as a vertical timeline with dots, labels, and dates.
- Admin notes are rendered inline.

---

## 2. Decision Matrix

| Field                    | Exists?  | API Source                                  | Backend Change Required?                         |
|--------------------------|----------|---------------------------------------------|-------------------------------------------------|
| Order Status (current)   | ✅ Yes   | `OrderDetailResponse.status`                | None                                             |
| Status History           | ✅ Yes   | `OrderDetailResponse.statusHistory`         | None                                             |
| Status Timestamps        | ✅ Yes   | `OrderStatusHistory.createdAt`              | None                                             |
| Tracking Number          | ❌ No    | —                                           | Add column `tracking_number` to `orders` table  |
| Courier Name             | ❌ No    | —                                           | Add column `courier_name` to `orders` table     |
| Tracking URL             | ❌ No    | —                                           | Add column `tracking_url` to `orders` table     |
| Estimated Delivery Date  | ❌ No    | —                                           | Add column `estimated_delivery_at` to `orders`  |
| Actual Delivery Date     | ⚠️ Implicit | `statusHistory[DELIVERED].createdAt`    | None (derivable) — or add `delivered_at` column |
| Shipment Creation Date   | ⚠️ Implicit | `statusHistory[SHIPPED].createdAt`      | None (derivable) — or add `shipped_at` column   |
| `OUT_FOR_DELIVERY` step  | ❌ No    | —                                           | Add to `OrderStatus` enum                        |
| Order Timeline UI events | ✅ Yes   | `OrderDetailResponse.statusHistory`         | None — already exists and rendered on frontend  |

---

## 3. Missing Capabilities — Minimum Required Backend Enhancement

To implement an enterprise-grade visual order tracking experience comparable to Nike, Zara, ASOS, the following minimum backend enhancements are required:

### 3.1 Database Changes

#### `orders` table — add 4 columns:

```sql
ALTER TABLE orders
  ADD COLUMN tracking_number     VARCHAR(100)  DEFAULT NULL,
  ADD COLUMN courier_name        VARCHAR(100)  DEFAULT NULL,
  ADD COLUMN tracking_url        VARCHAR(500)  DEFAULT NULL,
  ADD COLUMN estimated_delivery_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
```

> `delivered_at` and `shipped_at` are **NOT required** — they are reliably derivable from the existing `order_status_history` table by selecting `createdAt` where `status = 'DELIVERED'` or `'SHIPPED'`. Adding dedicated columns would be redundant.

#### `OrderStatus` enum — add 1 value:

```java
/** Courier has departed for final delivery. Set by admin. */
OUT_FOR_DELIVERY,
```

Transition rules to add:
- `SHIPPED → OUT_FOR_DELIVERY`
- `OUT_FOR_DELIVERY → DELIVERED`

---

### 3.2 Entity Changes

#### `Order.java` — add 4 fields:

```java
@Column(name = "tracking_number", length = 100)
private String trackingNumber;

@Column(name = "courier_name", length = 100)
private String courierName;

@Column(name = "tracking_url", length = 500)
private String trackingUrl;

@Column(name = "estimated_delivery_at")
private Instant estimatedDeliveryAt;
```

---

### 3.3 DTO Changes

#### `OrderDetailResponse.java` — add 4 fields:

```java
/** Courier tracking number (e.g. "DTDC123456789IN"). Null until order is shipped. */
private String trackingNumber;

/** Courier company name (e.g. "Delhivery", "DTDC", "BlueDart"). Null until order is shipped. */
private String courierName;

/** Deep link to courier's tracking page. Null until order is shipped. */
private String trackingUrl;

/** Estimated delivery date communicated to the customer. Null until shipment. */
private Instant estimatedDeliveryAt;
```

Map these in `OrderService.toDetailResponse()`.

#### `UpdateOrderStatusRequest.java` — add 4 optional fields:

```java
/** Courier tracking number — set when advancing to SHIPPED or OUT_FOR_DELIVERY. */
private String trackingNumber;

/** Courier name — set when advancing to SHIPPED or OUT_FOR_DELIVERY. */
private String courierName;

/** Courier tracking URL. */
private String trackingUrl;

/** Estimated delivery date. */
private Instant estimatedDeliveryAt;
```

In `OrderService.adminUpdateStatus()`, when status is `SHIPPED` or `OUT_FOR_DELIVERY`, copy these from the request to the `Order` entity.

---

### 3.4 Endpoint Changes

No new endpoints are required. The existing endpoint `PUT /api/v1/admin/orders/{orderId}/status` is sufficient. It only needs to accept and persist the new optional fields in the request body.

The existing `GET /api/v1/orders/{orderId}` response is sufficient once the DTO is expanded.

---

## 4. Frontend Readiness Assessment

| Component                        | Ready?  | Notes                                         |
|----------------------------------|---------|-----------------------------------------------|
| `OrderDetail` type               | ⚠️ Partial | Missing 4 new shipment fields                |
| `OrderStatusHistory` type        | ✅ Yes  | Already typed correctly                       |
| `CustomerOrderDetailPage.tsx`    | ⚠️ Partial | Status timeline renders; missing shipment info section |
| Status label mapping             | ✅ Yes  | All current statuses mapped in `STATUS_LABELS`; `OUT_FOR_DELIVERY` missing |
| Visual timeline component        | ✅ Yes  | Already exists with dots/labels/timestamps    |
| Courier tracking CTA             | ❌ No   | No "Track Shipment" button/link component     |
| Estimated delivery display       | ❌ No   | No estimated delivery date display            |

---

## 5. Summary Decision

### ❌ Backend is NOT fully ready for Order Tracking UI.

The existing infrastructure is strong:
- ✅ Status history (timeline data) is fully implemented.
- ✅ Per-status timestamps are captured accurately.
- ✅ Admin notes are already part of history entries.

But the following gaps block an enterprise-grade tracking experience:
- ❌ No `trackingNumber`, `courierName`, `trackingUrl` fields exist anywhere in the schema, entity, or DTOs.
- ❌ No `estimatedDeliveryAt` field.
- ❌ No `OUT_FOR_DELIVERY` status step.

### Required before frontend implementation:

1. Add 4 columns to `orders` table (tracking + ETA fields).
2. Add `OUT_FOR_DELIVERY` to `OrderStatus` enum with valid transitions.
3. Expand `Order` entity with the 4 new fields.
4. Expand `OrderDetailResponse` DTO to expose the new fields.
5. Expand `UpdateOrderStatusRequest` to accept shipment info when advancing to `SHIPPED` / `OUT_FOR_DELIVERY`.
6. Map fields in `OrderService.adminUpdateStatus()` and `toDetailResponse()`.

These are additive changes. They do NOT break any existing API consumers.
