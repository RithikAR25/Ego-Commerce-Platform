# Coupons

## What

Admin-managed discount codes applied at checkout. Supports fixed-amount (`FLAT`) and percentage-based (`PERCENTAGE`) discounts with configurable constraints. Single coupon per order. Case-insensitive coupon codes stored in uppercase.

## Why

Coupon codes are a fundamental CRO and marketing tool — enabling flash sales, loyalty rewards, and new-customer acquisition discounts without engineering effort. The checkout integration is atomic to prevent partial discount application.

## Backend

**Module:** `com.ego.raw_ego.coupon`

| File | Responsibility |
|---|---|
| `Coupon.java` | Entity — discount type, value, constraints, uses tracking |
| `OrderCoupon.java` | Audit entity — records which coupon was used on which order |
| `DiscountType.java` | `FLAT`, `PERCENTAGE` enum |
| `CouponService.java` | Validation, discount computation, CRUD |
| `CouponController.java` | Public validate + Admin CRUD endpoints |

**Discount computation (source-verified from `Coupon.java` + `OrderService.java`):**
```
if FLAT:
    discount = min(coupon.discountValue, subtotal)

if PERCENTAGE:
    discount = subtotal × (coupon.discountValue / 100)
    if coupon.maxDiscountAmount is not null:
        discount = min(discount, coupon.maxDiscountAmount)

grandTotal = max(subtotal - discount, 0) + shippingTotal
```

**Checkout integration:** Coupon validation, discount computation, `OrderCoupon` audit record, and `coupon.currentUses` increment all happen **inside the existing `@Transactional checkout()` boundary**. An invalid coupon rolls back inventory commits already made in the same transaction.

**Concurrency:** `currentUses` is incremented via a `@Modifying(clearAutomatically = true)` JPQL query — safe under concurrent checkouts.

## Database

**Table: `coupons`**

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `code` | VARCHAR(50) | Unique, stored UPPERCASE |
| `description` | VARCHAR(255) | Admin-readable label |
| `discount_type` | ENUM | `FLAT`, `PERCENTAGE` |
| `discount_value` | DECIMAL(10,2) | Rupee amount or percentage number |
| `max_discount_amount` | DECIMAL(10,2) | PERCENTAGE cap (nullable = no cap) |
| `min_order_amount` | DECIMAL(10,2) | Min subtotal required (nullable = no min) |
| `max_uses` | INT | Total use limit (nullable = unlimited) |
| `current_uses` | INT | Running usage count (default 0) |
| `active` | BOOLEAN | Soft-delete flag (default true) |
| `expires_at` | DATETIME | Expiry (nullable = never expires) |
| `created_at` | DATETIME | Immutable |
| `updated_at` | DATETIME | Last change |

**Table: `order_coupons`** — audit trail

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `order_id` | BIGINT UNSIGNED | FK → orders.id |
| `coupon_id` | BIGINT UNSIGNED | FK → coupons.id |
| `discount_amount` | DECIMAL(10,2) | Actual discount applied to this order |
| `created_at` | DATETIME | When the coupon was applied |

## API

### Public Endpoint (no auth)

**`GET /api/v1/coupons/validate?code=X&subtotal=Y`**
```json
// Valid coupon
{
  "valid": true,
  "code": "SAVE20",
  "discountType": "PERCENTAGE",
  "discountValue": 20.0,
  "discountAmount": 260.00,
  "finalSubtotal": 1040.00,
  "message": "20% off applied — you save ₹260"
}

// Invalid coupon
{
  "valid": false,
  "code": "EXPIRED100",
  "message": "This coupon has expired"
}
```
Zero side effects — does not increment `currentUses`.

### Admin Endpoints (ROLE_ADMIN required)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/admin/coupons` | List all coupons with usage stats |
| `POST` | `/api/v1/admin/coupons` | Create coupon |
| `PUT` | `/api/v1/admin/coupons/{id}` | Update coupon |
| `DELETE` | `/api/v1/admin/coupons/{id}` | Soft-deactivate (`active=false`) |

**Create request body:**
```json
{
  "code": "SUMMER25",
  "description": "Summer sale — 25% off all orders",
  "discountType": "PERCENTAGE",
  "discountValue": 25.0,
  "maxDiscountAmount": 1500.00,
  "minOrderAmount": 500.00,
  "maxUses": 1000,
  "expiresAt": "2026-08-31T23:59:59Z"
}
```

## Validation Rules

**Coupon validation at checkout (all must pass):**
1. Code exists and `active = true` → `400`
2. Not expired (`expiresAt == null || expiresAt > now`) → `400`
3. Usage limit not reached (`maxUses == null || currentUses < maxUses`) → `409`
4. Minimum order met (`minOrderAmount == null || subtotal >= minOrderAmount`) → `400`

**Code normalization:** Input code uppercased at service layer before lookup — `"save20"` → `"SAVE20"`.

**Soft-delete:** Coupons are deactivated, never hard-deleted — preserves `order_coupons` FK integrity for historical reporting.

## Security

- `GET /api/v1/coupons/validate` is in `PUBLIC_MATCHERS` — accessible without auth (needed for cart preview before login)
- Admin CRUD requires `ROLE_ADMIN`
- `currentUses` cannot be manipulated via API — only incremented internally at checkout

## Known Limitations

- One coupon per order — stacking not supported
- No per-user coupon limits (a single user could use all `maxUses` alone)
- No first-time-use-only enforcement (would require `order_coupons` check per user)

## Extension Points

- Add `user_id` column to `order_coupons` — enables per-user use limits
- Add `min_uses_per_user` / `max_uses_per_user` constraints on `Coupon`
- Add coupon categories — restrict to specific product categories

## Source References

- `raw-ego/src/main/java/com/ego/raw_ego/coupon/entity/Coupon.java`
- `raw-ego/src/main/java/com/ego/raw_ego/coupon/enums/DiscountType.java`
- `docs/database/schema_coupons.sql`
