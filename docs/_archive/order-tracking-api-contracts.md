# Order Tracking — Backend API Contracts

**Status:** Implemented  
**Phase:** Order Tracking Enhancement (post-audit)

---

## Overview

The order tracking backend adds shipment visibility fields to the existing order domain.
No new endpoints were created. All existing endpoints are backward-compatible — new fields
return `null` for orders that predate this enhancement.

---

## Updated: `PUT /api/v1/admin/orders/{orderId}/status`

Advances the order to the next lifecycle status and optionally persists shipment tracking data.

### Request Body

```json
{
  "status": "SHIPPED",
  "note": "Handed to Delhivery. AWB: DELHIVERY9876543210",
  "trackingNumber": "DELHIVERY9876543210",
  "courierName": "Delhivery",
  "trackingUrl": "https://www.delhivery.com/track/package/DELHIVERY9876543210",
  "estimatedDeliveryAt": "2026-06-10T18:30:00Z"
}
```

| Field                | Type     | Required | Notes                                                   |
|----------------------|----------|----------|---------------------------------------------------------|
| `status`             | String   | ✅ Yes   | Must be a valid `OrderStatus` enum value                |
| `note`               | String   | ❌ No    | Free-text admin note attached to the history entry      |
| `trackingNumber`     | String   | ❌ No    | Persisted when `status` is `SHIPPED` or `OUT_FOR_DELIVERY`. Max 100 chars. |
| `courierName`        | String   | ❌ No    | Persisted when `status` is `SHIPPED` or `OUT_FOR_DELIVERY`. Max 100 chars. |
| `trackingUrl`        | String   | ❌ No    | Persisted when `status` is `SHIPPED` or `OUT_FOR_DELIVERY`. Max 500 chars. |
| `estimatedDeliveryAt`| Instant  | ❌ No    | ISO-8601 UTC timestamp. Persisted when `status` is `SHIPPED` or `OUT_FOR_DELIVERY`. |

> **Nullability rule:** If a tracking field is omitted (`null`) in the request, the existing
> DB value is preserved. This allows updating only the status without clearing existing tracking data.

### Valid Status Transitions (Updated)

```
PENDING_PAYMENT  → CONFIRMED | CANCELLED
CONFIRMED        → PROCESSING | CANCELLED
PROCESSING       → SHIPPED
SHIPPED          → OUT_FOR_DELIVERY | DELIVERED   ← UPDATED
OUT_FOR_DELIVERY → DELIVERED                       ← NEW
```

---

## Updated: `GET /api/v1/orders/{orderId}` and `GET /api/v1/admin/orders/{orderId}`

The `OrderDetailResponse` now includes shipment tracking fields.

### Response Body (new fields highlighted)

```json
{
  "id": 42,
  "status": "SHIPPED",
  "subtotal": 2999.00,
  "shippingTotal": 0.00,
  "discountAmount": 0.00,
  "couponCode": null,
  "grandTotal": 2999.00,
  "shippingAddress": "Rithik A, 12 MG Road, Bengaluru 560001",
  "razorpayOrderId": "order_XXXXXXXXXXXXXXXXXX",

  "trackingNumber": "DELHIVERY9876543210",
  "courierName": "Delhivery",
  "trackingUrl": "https://www.delhivery.com/track/package/DELHIVERY9876543210",
  "estimatedDeliveryAt": "2026-06-10T18:30:00Z",

  "items": [ ... ],
  "statusHistory": [
    { "status": "PENDING_PAYMENT", "note": "Order placed by customer.", "createdAt": "2026-06-02T10:00:00Z" },
    { "status": "CONFIRMED",       "note": null,                         "createdAt": "2026-06-02T11:00:00Z" },
    { "status": "PROCESSING",      "note": null,                         "createdAt": "2026-06-03T08:00:00Z" },
    { "status": "SHIPPED",         "note": "Handed to Delhivery.",       "createdAt": "2026-06-04T09:00:00Z" }
  ],
  "createdAt": "2026-06-02T10:00:00Z",
  "updatedAt": "2026-06-04T09:00:00Z"
}
```

### New Fields in `OrderDetailResponse`

| Field                | Type    | Nullable | Source                                          |
|----------------------|---------|----------|-------------------------------------------------|
| `trackingNumber`     | String  | Yes      | Set by admin when advancing to SHIPPED/OUT_FOR_DELIVERY |
| `courierName`        | String  | Yes      | Set by admin when advancing to SHIPPED/OUT_FOR_DELIVERY |
| `trackingUrl`        | String  | Yes      | Set by admin when advancing to SHIPPED/OUT_FOR_DELIVERY |
| `estimatedDeliveryAt`| String  | Yes      | ISO-8601 UTC. Set by admin at shipment time     |

> **Backward compatibility:** All 4 fields are `null` for orders that were created/shipped before
> this enhancement. The response shape is additive — no breaking changes.

---

## `OrderStatus` Enum (Updated)

```java
PENDING_PAYMENT   // Order created; awaiting payment
CONFIRMED         // Payment confirmed
PROCESSING        // Being packed at warehouse
SHIPPED           // Handed to courier (tracking available)
OUT_FOR_DELIVERY  // Courier departed for final delivery [NEW]
DELIVERED         // Successfully delivered [Terminal]
CANCELLED         // Cancelled [Terminal]
REFUNDED          // Returned and refunded [Terminal]
```

---

## Database Changes

Columns added to `orders` table via Hibernate `ddl-auto=update` (additive — no data loss):

| Column                  | Type          | Nullable | Default |
|-------------------------|---------------|----------|---------|
| `tracking_number`       | VARCHAR(100)  | Yes      | NULL    |
| `courier_name`          | VARCHAR(100)  | Yes      | NULL    |
| `tracking_url`          | VARCHAR(500)  | Yes      | NULL    |
| `estimated_delivery_at` | DATETIME(6)   | Yes      | NULL    |

---

## Frontend Integration Notes

The frontend `OrderDetail` type must be updated to include the 4 new fields:

```typescript
export interface OrderDetail {
  // ... existing fields ...
  trackingNumber:      string | null;
  courierName:         string | null;
  trackingUrl:         string | null;
  estimatedDeliveryAt: string | null; // ISO-8601 UTC
}
```

The `OrderStatus` type must also include `'OUT_FOR_DELIVERY'`.

Display logic:
- Show the "Track Shipment" button/link when `trackingUrl !== null`
- Show estimated delivery when `estimatedDeliveryAt !== null`
- Show `OUT_FOR_DELIVERY` in the status timeline as "Out for Delivery"
