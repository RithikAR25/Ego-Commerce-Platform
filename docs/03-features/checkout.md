# Checkout

## What

A 3-step checkout flow (Address → Review → Pay) backed by an atomic single-transaction server-side checkout pipeline. Inventory is committed, the order is persisted, the cart is cleared, and an order confirmation email is queued — all in one `@Transactional` operation.

## Why

- **Single `@Transactional`:** If inventory commit fails for any item, the entire order creation rolls back — no partial inventory deductions and no orphaned orders.
- **`saveAndFlush()`:** Required so the `OrderDetailResponse` DTO (built in the same transaction) sees the DB-generated `id`, `createdAt`, and `updatedAt` values.
- **Optional coupon:** Coupons are validated and applied inside the same transaction — an invalid coupon after cart validation triggers a full rollback.

## Frontend

**Layout:** `CheckoutLayout` — header-only layout (no navbar/footer), prevents accidental navigation away.

**Stepper:** MUI `<Stepper>` with 3 steps:
1. **Address** — select from saved addresses or enter new one
2. **Review** — cart items, apply coupon, total breakdown
3. **Payment** — Razorpay Checkout.js modal

**Route:** `/checkout` → `CheckoutPage.tsx` (protected — requires auth)

## Checkout Pipeline (Source-Verified)

See full diagram → [04-flows/checkout-flow.md](../04-flows/checkout-flow.md)

**Summary (`OrderService.checkout()` — single `@Transactional`):**
1. Load Redis cart → validate not empty → `400` if empty
2. For each cart item: `InventoryReservationService.commit(variantId, qty)` → atomic: `qty_reserved -= qty`, `qty_available -= qty` via `@Modifying(clearAutomatically=true)` with optimistic lock
3. Apply coupon discount if `couponCode` provided
4. Persist `Order` + `OrderItems` (5 snapshots) + `OrderStatusHistory(PENDING_PAYMENT)`
5. `saveAndFlush()` → get DB-generated IDs and timestamps
6. `CartService.clearCart(userId)` → Redis `DEL cart:{userId}`
7. Return `OrderDetailResponse`
8. (After transaction commit, async) → `OrderPlacedEvent` → SendGrid order confirmation email

## Request / Response

**`POST /api/v1/orders/checkout`**
```json
// Request
{
  "addressId": 3,
  "couponCode": "SAVE20",
  "notes": "Leave at door if no one home"
}

// Response 201
{
  "id": 42,
  "status": "PENDING_PAYMENT",
  "subtotal": 2598.00,
  "discountAmount": 519.60,
  "shippingTotal": 0.00,
  "grandTotal": 2078.40,
  "couponCodeSnapshot": "SAVE20",
  "razorpayOrderId": null,
  "items": [
    {
      "variantId": 5,
      "quantity": 2,
      "skuSnapshot": "EGO-TEE-0001-BLK-M",
      "productNameSnapshot": "Classic Black Oversized Tee",
      "variantLabelSnapshot": "Black / M",
      "primaryImageUrlSnapshot": "https://res.cloudinary.com/...",
      "unitPriceSnapshot": 1299.00
    }
  ]
}
```

## Validation Rules

| Check | Response on failure |
|---|---|
| Cart not empty | `400 Bad Request` |
| All variants active | `409 Conflict: variant unavailable` |
| Inventory sufficient for all items | `409 Conflict: insufficient stock` |
| `addressId` belongs to authenticated user | `404 Not Found` |
| Coupon code valid (if provided) | `400 Bad Request: {reason}` |
| User is authenticated | `401 Unauthorized` (caught by `ProtectedRoute` frontend + JWT filter backend) |

## Price Formula

```
subtotal      = Σ (unitPrice × quantity) for all cart items
discount      = FLAT: min(couponValue, subtotal)
              = PERCENTAGE: min(subtotal × rate, maxCap ?? ∞)
shippingTotal = free (currently 0.00 for all orders)
grandTotal    = max(subtotal - discount, 0) + shippingTotal
```

## After Checkout

Payment flow continues at:
- `POST /api/v1/payments/razorpay/create {orderId}` → get Razorpay payment order
- Frontend opens Razorpay Checkout.js modal
- On payment: Razorpay webhook → `PENDING_PAYMENT → CONFIRMED`
- Poll `GET /api/v1/orders/{id}` until `status == CONFIRMED`
- Redirect to `/checkout/success/{orderId}`

See: [payments.md](payments.md) · [04-flows/checkout-flow.md](../04-flows/checkout-flow.md)

## Known Limitations

- Shipping is always `0.00` — no shipping zone/carrier integration
- No split-address delivery — one shipping address per order
- No draft order support — checkout is atomic and immediate

## Source References

- `raw-ego/src/main/java/com/ego/raw_ego/order/service/OrderService.java` — `checkout()` method
- `raw-ego-frontend/src/features/checkout/pages/CheckoutPage.tsx`
- [04-flows/checkout-flow.md](../04-flows/checkout-flow.md) — full Mermaid pipeline diagram
