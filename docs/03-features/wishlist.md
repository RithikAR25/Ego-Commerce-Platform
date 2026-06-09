# Wishlist

## What

Variant-level wishlist backed by MySQL. Customers can save specific variants (a particular color and size combination) for later. Idempotent — no error on duplicate add or missing remove.

## Why

- **Variant-level (not product-level):** Saves the exact item the customer wants (Black / M), not just "this product." Reduces friction when moving to cart.
- **MySQL (not Redis):** Wishlist data is durable — survives Redis flush, TTL expiry, and device change. Owned by the user account.

## Backend

**Module:** `com.ego.raw_ego.wishlist`

**Key invariant:** `UNIQUE(user_id, variant_id)` enforced at DB level.

**GET live data:** Batch-fetches all variant catalog data (price, stock, image) in one `JOIN` SQL query — zero N+1 queries. Catalog-deleted variants silently dropped on GET.

## Database

**Table: `wishlist_items`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `user_id` | BIGINT UNSIGNED | FK → users.id |
| `variant_id` | BIGINT UNSIGNED | FK → product_variants.id |
| `created_at` | DATETIME | When added to wishlist |

**Constraint:** `UNIQUE(user_id, variant_id)`

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/wishlist` | Customer JWT | List wishlist with live prices |
| `POST` | `/api/v1/wishlist/items` | Customer JWT | Add variant (`{variantId}`) |
| `DELETE` | `/api/v1/wishlist/items/{variantId}` | Customer JWT | Remove variant |
| `DELETE` | `/api/v1/wishlist` | Customer JWT | Clear wishlist |

**Idempotency:**
- `POST` with already-wishlisted variant → `200 OK` (not `409`)
- `DELETE` of non-existent variant → `200 OK` (not `404`)

**GET response item:**
```json
{
  "variantId": 5,
  "productName": "Classic Black Oversized Tee",
  "variantLabel": "Black / M",
  "price": 1299.00,
  "compareAtPrice": 1999.00,
  "primaryImageUrl": "https://res.cloudinary.com/...",
  "stockStatus": "IN_STOCK",
  "quantityAvailable": 8,
  "slug": "classic-black-oversized-tee"
}
```

## Validation Rules

- `variantId` must reference an existing, active variant
- All endpoints require authentication

## Source References

- `raw-ego/src/main/java/com/ego/raw_ego/wishlist/`
- `docs/database/schema_wishlist_items.sql`
