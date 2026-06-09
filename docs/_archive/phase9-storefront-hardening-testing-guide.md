# Phase 9 — Storefront Hardening Testing Guide

This guide covers the full E2E testing procedure for the three Phase 9 features. Run all tests via **Swagger UI** at `http://localhost:8080/swagger-ui.html` unless otherwise specified.

---

## Prerequisites

1. Backend running: `mvnw spring-boot:run`
2. MySQL schema updated: run all three Phase 9 SQL files:
   - `docs/database/schema_product_reviews.sql`
   - `docs/database/schema_wishlist_items.sql`
   - `docs/database/schema_coupons.sql`
3. At least one product with variants in the database
4. At least one customer account with a **DELIVERED** order (for review eligibility)

---

## 9A — Purchase-Gated Reviews

### Test A1: Attempt review without purchase (expect 409)
```
POST /api/v1/products/{productId}/reviews
Authorization: Bearer {customer_token}
Body: { "rating": 5, "title": "Great!", "body": "Loved it." }

Expected: 409 Conflict
Message: "You can only review products you have purchased and received..."
```

### Test A2: Advance an order to DELIVERED status
```
PUT /api/v1/admin/orders/{orderId}/status
Authorization: Bearer {admin_token}
Body: { "status": "CONFIRMED", "note": "Confirmed." }
→ repeat for PROCESSING, SHIPPED, DELIVERED (one step at a time)
```

### Test A3: Submit valid review (expect 201)
```
POST /api/v1/products/{productId}/reviews
Authorization: Bearer {customer_token}
Body: { "rating": 4, "title": "Solid quality", "body": "True to size, great material." }

Expected: 201 Created
Verify DB: SELECT * FROM product_reviews WHERE product_id = {productId};
→ Row with correct rating, title, body, reviewer_first_name
```

### Test A4: Submit duplicate review (expect 409)
```
POST /api/v1/products/{productId}/reviews   (same request as A3)

Expected: 409 Conflict
Message: "You have already submitted a review for this product."
```

### Test A5: Read reviews (public, no auth)
```
GET /api/v1/products/{productId}/reviews

Expected: 200 OK
→ Page of reviews. reviewerFirstName visible, NO userId or email.
```

### Test A6: Rating summary (public, no auth)
```
GET /api/v1/products/{productId}/reviews/summary

Expected: 200 OK
→ averageRating (not null), reviewCount ≥ 1, ratingBreakdown map with keys 1-5
```

### Test A7: Validation — rating out of range (expect 400)
```
POST /api/v1/products/{productId}/reviews
Body: { "rating": 6 }

Expected: 400 Bad Request — validation error on rating field
```

### Test A8: Admin delete review
```
DELETE /api/v1/admin/reviews/{reviewId}
Authorization: Bearer {admin_token}

Expected: 200 OK
Verify DB: SELECT * FROM product_reviews WHERE id = {reviewId}; → 0 rows
```

---

## 9B — Wishlists

### Test B1: Get empty wishlist
```
GET /api/v1/wishlist
Authorization: Bearer {customer_token}

Expected: 200 OK
→ { "items": [], "itemCount": 0 }
```

### Test B2: Add variant to wishlist
```
POST /api/v1/wishlist/items
Authorization: Bearer {customer_token}
Body: { "variantId": 1 }

Expected: 200 OK
→ WishlistResponse with item. Verify DB: SELECT * FROM wishlist_items WHERE user_id = {userId};
```

### Test B3: Add same variant again (idempotent, expect 200)
```
POST /api/v1/wishlist/items
Body: { "variantId": 1 }   (same as B2)

Expected: 200 OK — no duplicate row created
Verify DB: SELECT COUNT(*) FROM wishlist_items WHERE user_id = {userId}; → still 1 row
```

### Test B4: Add non-existent variant (expect 404)
```
POST /api/v1/wishlist/items
Body: { "variantId": 999999 }

Expected: 404 Not Found — "Variant not found: id=999999"
```

### Test B5: Add second variant
```
POST /api/v1/wishlist/items
Body: { "variantId": 2 }

Expected: 200 OK — WishlistResponse now has 2 items
→ Check stockStatus, price, primaryImageUrl in response
```

### Test B6: Remove variant (idempotent)
```
DELETE /api/v1/wishlist/items/2
Authorization: Bearer {customer_token}

Expected: 200 OK — WishlistResponse with 1 item remaining
```

### Test B7: Remove variant not in wishlist (idempotent, expect 200)
```
DELETE /api/v1/wishlist/items/999
Authorization: Bearer {customer_token}

Expected: 200 OK — silent no-op
```

### Test B8: Clear wishlist
```
DELETE /api/v1/wishlist
Authorization: Bearer {customer_token}

Expected: 200 OK
Verify DB: SELECT COUNT(*) FROM wishlist_items WHERE user_id = {userId}; → 0 rows
```

---

## 9C — Coupon Codes

### Test C1: Create coupon (admin)
```
POST /api/v1/admin/coupons
Authorization: Bearer {admin_token}
Body:
{
  "code": "EGO20",
  "description": "20% off summer sale",
  "discountType": "PERCENTAGE",
  "discountValue": 20.00,
  "maxDiscountAmount": 500.00,
  "minOrderAmount": 500.00,
  "maxUses": 100,
  "expiresAt": null
}

Expected: 201 Created
Verify DB: SELECT * FROM coupons WHERE code = 'EGO20';
→ active=1, current_uses=0, discount_type='PERCENTAGE'
```

### Test C2: Create flat coupon
```
POST /api/v1/admin/coupons
Body:
{
  "code": "FLAT200",
  "discountType": "FLAT",
  "discountValue": 200.00,
  "minOrderAmount": 1000.00
}

Expected: 201 Created
```

### Test C3: Validate coupon (public endpoint, no auth)
```
GET /api/v1/coupons/validate?code=EGO20&subtotal=2000.00

Expected: 200 OK
{
  "valid": true,
  "code": "EGO20",
  "discountType": "PERCENTAGE",
  "discountValue": 20.00,
  "estimatedDiscount": 400.00,   ← 20% of 2000 = 400, capped at 500 ✓
  "estimatedTotal": 1600.00
}
```

### Test C4: Validate — min amount not met
```
GET /api/v1/coupons/validate?code=EGO20&subtotal=300.00

Expected: 200 OK
{
  "valid": false,
  "reason": "A minimum order of ₹500.00 is required to use coupon \"EGO20\"."
}
```

### Test C5: Validate — non-existent code
```
GET /api/v1/coupons/validate?code=FAKECODE&subtotal=1000.00

Expected: 200 OK
{ "valid": false, "reason": "Coupon code \"FAKECODE\" is invalid or has expired." }
```

### Test C6: Checkout without coupon (baseline)
Ensure checkout still works with no couponCode field.
```
POST /api/v1/orders/checkout
Authorization: Bearer {customer_token}
Body: { "shippingAddress": "123 MG Road, Bengaluru" }

Expected: 201 Created
→ discountAmount: 0.00, couponCode: null, grandTotal = subtotal
```

### Test C7: Checkout WITH coupon applied ⭐
```
Add items to cart first. Subtotal must be ≥ ₹500 for EGO20.

POST /api/v1/orders/checkout
Authorization: Bearer {customer_token}
Body:
{
  "shippingAddress": "123 MG Road, Bengaluru",
  "couponCode": "EGO20"
}

Expected: 201 Created
→ discountAmount: {20% of subtotal, capped at ₹500}
→ couponCode: "EGO20"
→ grandTotal: subtotal - discountAmount

Verify DB:
  SELECT discount_amount, coupon_code_snapshot FROM orders WHERE id = {orderId};
  → correct values

  SELECT * FROM order_coupons WHERE order_id = {orderId};
  → one row: coupon_code = 'EGO20', discount_amount = correct

  SELECT current_uses FROM coupons WHERE code = 'EGO20';
  → current_uses incremented by 1
```

### Test C8: Invalid coupon at checkout (expect 400/409)
```
POST /api/v1/orders/checkout
Body: { "shippingAddress": "...", "couponCode": "INVALIDCODE" }

Expected: 409 Conflict
Message: "Coupon code \"INVALIDCODE\" is invalid or has expired."
Note: Inventory commits are rolled back — no orphaned inventory deduction.
```

### Test C9: Update coupon (admin)
```
PUT /api/v1/admin/coupons/{id}
Body: { "active": false }

Expected: 200 OK — coupon deactivated
Verify: GET /api/v1/coupons/validate?code=EGO20&subtotal=1000 → valid: false
```

### Test C10: Expired coupon
```
POST /api/v1/admin/coupons
Body: { "code": "EXPIRED10", "discountType": "FLAT", "discountValue": 100, "expiresAt": "2020-01-01T00:00:00Z" }

GET /api/v1/coupons/validate?code=EXPIRED10&subtotal=1000
Expected: { "valid": false, "reason": "Coupon \"EXPIRED10\" has expired." }
```

### Test C11: Max uses exhausted
```
POST /api/v1/admin/coupons
Body: { "code": "ONETIME", "discountType": "FLAT", "discountValue": 50, "maxUses": 1 }

→ Use it once at checkout
→ Try to validate again: { "valid": false, "reason": "...usage limit..." }
```

### Test C12: Duplicate coupon code (expect 409)
```
POST /api/v1/admin/coupons
Body: { "code": "EGO20", ... }

Expected: 409 Conflict — "Coupon code \"EGO20\" already exists."
```

---

## Quick SQL Verification Queries

```sql
-- Review check
SELECT id, product_id, user_id, reviewer_first_name, rating, title
FROM product_reviews ORDER BY created_at DESC LIMIT 10;

-- Wishlist check
SELECT wi.id, wi.user_id, wi.variant_id, pv.sku
FROM wishlist_items wi
JOIN product_variants pv ON wi.variant_id = pv.id
ORDER BY wi.created_at DESC LIMIT 20;

-- Coupon usage audit
SELECT o.id AS order_id, o.grand_total, o.discount_amount, o.coupon_code_snapshot,
       oc.coupon_code, oc.discount_amount AS audit_discount
FROM orders o
LEFT JOIN order_coupons oc ON o.id = oc.order_id
WHERE o.coupon_code_snapshot IS NOT NULL
ORDER BY o.created_at DESC LIMIT 10;

-- Coupon stats
SELECT code, discount_type, discount_value, current_uses, max_uses, active, expires_at
FROM coupons ORDER BY created_at DESC;
```
