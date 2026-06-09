# Reviews

## What

Purchase-gated product reviews. Only customers who have a `DELIVERED` order containing the product can submit a review. One review per user per product. Auto-approved (no moderation queue).

## Why

Purchase-gated reviews prevent fake reviews from users who have never bought the product. The DELIVERED gate also ensures reviewers have actually received the item.

## Backend

**Module:** `com.ego.raw_ego.review`

**Eligibility check (source-verified JPQL):**
```sql
SELECT COUNT(oi) > 0
FROM OrderItem oi
JOIN oi.variant v
JOIN v.product p
WHERE oi.order.user.id = :userId
  AND p.id = :productId
  AND oi.order.status = 'DELIVERED'
```
This joins `order_items → product_variants → products` without importing cross-module entities.

## Database

**Table: `product_reviews`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `product_id` | BIGINT UNSIGNED | FK → products.id |
| `user_id` | BIGINT UNSIGNED | FK → users.id |
| `rating` | TINYINT | 1–5 |
| `title` | VARCHAR(255) | Review headline |
| `body` | TEXT | Full review text |
| `created_at` | DATETIME | Immutable |

**Constraint:** `UNIQUE(user_id, product_id)` — one review per user per product

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/products/{id}/reviews` | Customer JWT | Submit review |
| `GET` | `/api/v1/products/{id}/reviews` | Public | List reviews (paginated) |
| `GET` | `/api/v1/products/{id}/reviews/summary` | Public | Rating summary |
| `DELETE` | `/api/v1/admin/reviews/{id}` | ROLE_ADMIN | Admin delete |

**Rating summary response:**
```json
{
  "averageRating": 4.3,
  "totalCount": 47,
  "breakdown": { "5": 25, "4": 12, "3": 6, "2": 3, "1": 1 }
}
```
All 5 keys always present, zero-filled.

## Validation Rules

- `rating`: required, 1–5 integer
- `title`: required, max 255 chars
- `body`: optional, max 2000 chars
- Eligibility: DELIVERED order required → `403 Forbidden`
- Duplicate: second review attempt → `409 Conflict`

## ES Integration

`avgRating` and `reviewCount` are denormalized into `ProductDocument` at ES index time. Updated via `SearchIndexService.toDocument()` when a product is reindexed.

## Source References

- `raw-ego/src/main/java/com/ego/raw_ego/review/`
- `docs/database/schema_product_reviews.sql`
