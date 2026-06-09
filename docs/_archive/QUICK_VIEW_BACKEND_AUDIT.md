# Quick View Modal — Backend Readiness Audit

> **Date:** 2026-06-02  
> **Auditor:** Static analysis of all backend source, search documents, DTOs, controllers, and frontend integration code.  
> **Source of truth:** Java source, not documentation.

---

## Verdict

> **Backend is ready for Quick View. No backend changes are required.**

The existing `GET /api/v1/products/{slug}` endpoint returns a `ProductDetailResponse` that already contains every field required to power an enterprise-grade Quick View Modal — including all variant data, color/size attributes, gallery images, stock urgency messaging, and cart-compatible variant IDs.

The frontend implementation is **Option A** (preferred): reuse the existing endpoint, no new endpoint, no DTO changes, no workarounds.

---

## 1. Decision Matrix — Required Fields vs. Current Availability

| Required Field | Available? | API Source | Change Required |
|---|---|---|---|
| `productId` | ✅ Yes | `GET /api/v1/products/{slug}` → `data.id` | None |
| `slug` | ✅ Yes | `GET /api/v1/products/{slug}` → `data.slug` | None |
| `productName` | ✅ Yes | `data.name` | None |
| `shortDescription` | ✅ Yes | `data.description` (full description — already used on PDP, suitable for modal) | None |
| `primaryImage` | ✅ Yes | `data.variants[0].images[]` filtered on `primary=true`, fallback to `data.galleryImages[0]` | None |
| `galleryImages` | ✅ Yes | `data.galleryImages[]` (lifestyle shots) + `data.variants[].images[]` (variant-specific) | None |
| `price` | ✅ Yes | `data.variants[].price` — per variant | None |
| `compareAtPrice` | ✅ Yes | `data.variants[].compareAtPrice` (nullable) | None |
| `discountPercent` | ✅ Yes | `data.variants[].discountPercent` (pre-computed) | None |
| `stockStatus` | ✅ Yes | `data.variants[].stockStatus` → `IN_STOCK | LOW_STOCK | OUT_OF_STOCK` | None |
| `availableVariants` | ✅ Yes | `data.variants[]` (full array of all active variants) | None |
| `availableColors` | ✅ Yes | `data.attributeTypes[]` filtered on `name="Color"` → `.values[]` with `hexColor` + `swatchImageUrl` | None |
| `availableSizes` | ✅ Yes | `data.attributeTypes[]` filtered on `name="Size"` → `.values[]` | None |
| `quantityAvailable` | ✅ Yes | `data.variants[].quantityAvailable` — added by Stock Urgency feature (2026-06-02) | None |
| `stockUrgencyMessage` | ✅ Yes | `data.variants[].stockUrgencyMessage` — added by Stock Urgency feature (2026-06-02) | None |
| `lowStock` | ✅ Yes | `data.variants[].lowStock` — added by Stock Urgency feature (2026-06-02) | None |
| `rating` | ✅ Yes | Stored in ES `ProductDocument.avgRating`, but **NOT in `ProductDetailResponse`** | ⚠️ See note |
| `reviewCount` | ✅ Yes | Stored in ES `ProductDocument.reviewCount`, but **NOT in `ProductDetailResponse`** | ⚠️ See note |
| `variantId` (for Add to Cart) | ✅ Yes | `data.variants[].id` — directly usable in `POST /api/v1/cart` | None |
| `categoryName` (breadcrumb) | ✅ Yes | `data.category.name` | None |
| `attributeMatrix` (color × size) | ✅ Yes | `data.attributeTypes[]` + `data.variants[].attributeValues[]` | None |

### ⚠️ Rating & Review Count Note

`avgRating` and `reviewCount` are computed and stored in Elasticsearch's `ProductDocument` and populated via `SearchIndexService`. They are **not** currently included in `ProductDetailResponse`. 

However:
- The frontend already fetches `avgRating` and `reviewCount` for the PDP via a **separate Reviews API** (`GET /api/v1/products/{productId}/reviews`) rendered by the `ProductReviewsSection` component.
- For a Quick View Modal, the standard industry pattern is to **show a static rating summary only** (stars + count) without loading full reviews. This data is available **on the listing page already**, via `ProductDocument.avgRating` / `reviewCount` — which the frontend receives from `GET /api/v1/search` and `GET /api/v1/products` as part of the `ProductSummaryResponse` (if the frontend reads it from the ES path) or from the product card's data.

**Recommended approach:** Pass `avgRating` and `reviewCount` from the product card data (already present on the listing page from the ES search result) as **props** into the Quick View Modal rather than making a separate API call. This avoids a round trip and is standard Quick View UX (no one loads full reviews inside a Quick View).

**If** static rating is required from the detail endpoint, add two fields to `ProductDetailResponse`:

```java
private Float  avgRating;    // from ProductReviewRepository.findAverageRatingByProductId()
private Integer reviewCount; // from ProductReviewRepository.countByProductId()
```

This is a minor, non-breaking addition. No new endpoint needed.

---

## 2. Existing Capabilities — What the Backend Already Provides

### `GET /api/v1/products/{slug}` → `ProductDetailResponse`

Everything a Quick View Modal needs is in this single endpoint:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Oversized Acid-Wash Tee",
    "slug": "oversized-acid-wash-tee",
    "description": "...",
    "status": "ACTIVE",
    "category": { "id": 2, "name": "T-Shirts", "slug": "t-shirts" },
    "attributeTypes": [
      {
        "id": 1, "name": "Color",
        "values": [
          { "id": 10, "value": "Black", "code": "BLK", "hexColor": "#000000", "swatchImageUrl": null },
          { "id": 11, "value": "White", "code": "WHT", "hexColor": "#FFFFFF", "swatchImageUrl": null }
        ]
      },
      {
        "id": 2, "name": "Size",
        "values": [
          { "id": 20, "value": "S", "code": "S" },
          { "id": 21, "value": "M", "code": "M" },
          { "id": 22, "value": "L", "code": "L" }
        ]
      }
    ],
    "variants": [
      {
        "id": 12,
        "sku": "EGO-TEE-0001-BLK-M",
        "price": 1299.00,
        "compareAtPrice": 1799.00,
        "discountPercent": 28,
        "active": true,
        "stockStatus": "LOW_STOCK",
        "quantityAvailable": 3,
        "lowStock": true,
        "stockUrgencyMessage": "Only 3 left in your size",
        "weightGrams": 250,
        "attributeValues": [
          { "id": 10, "typeName": "Color", "value": "Black", "hexColor": "#000000" },
          { "id": 21, "typeName": "Size",  "value": "M" }
        ],
        "images": [
          { "id": 5, "url": "https://res.cloudinary.com/...", "primary": true, "displayOrder": 1 }
        ]
      }
    ],
    "galleryImages": [...],
    "minPrice": 1299.00,
    "maxPrice": 1499.00
  }
}
```

### Cart Integration — Already Supported

The Add to Cart API uses `variantId` directly:

```
POST /api/v1/cart
Authorization: Bearer <accessToken>
Body: { "variantId": 12, "quantity": 1 }
```

`variantId` is present on every `VariantResponse` object. No changes needed.

### Stock Urgency — Already Implemented (2026-06-02)

The Stock Urgency feature implemented on 2026-06-02 already handles all variant-level inventory messaging. The Quick View Modal gets this for free:

- `quantityAvailable` — raw units
- `lowStock` — boolean flag  
- `stockUrgencyMessage` — `"Only 3 left in your size"` | `"Selling fast"` | `null`

The `StockUrgencyIndicator` frontend component already exists and can be **dropped directly into the Quick View Modal** without modification.

---

## 3. Missing Capabilities

**None** — for the core Quick View feature.

The only optional enhancement is `avgRating` / `reviewCount` in `ProductDetailResponse`. This is **optional** since those fields are already available on the product card (from search/listing APIs) and can be passed as props.

---

## 4. Endpoint Changes

**None required.**

The Quick View Modal should call:

```
GET /api/v1/products/{slug}
```

This is an **existing, public, zero-auth endpoint** that returns everything needed. It is already used by the PDP and will cache well (same response, same cache key).

---

## 5. DTO Changes

**None required** for the core feature.

**Optional (non-breaking):** Add `avgRating` and `reviewCount` to `ProductDetailResponse` if the design requires showing a rating inside the Quick View from a single API call (rather than passing them from the product card).

```diff
// ProductDetailResponse.java
+ private Float  avgRating;
+ private Integer reviewCount;
```

```diff
// ProductService.getProductBySlug() and getProductBySlugAdmin()
+ .avgRating(reviewRepository.findAverageRatingByProductId(product.getId()) != null
+         ? reviewRepository.findAverageRatingByProductId(product.getId()).floatValue() : 0f)
+ .reviewCount((int) reviewRepository.countByProductId(product.getId()))
```

---

## 6. Frontend Integration Guidance

### Architecture Recommendation

The Quick View Modal should:

1. **Trigger:** User clicks a "Quick View" button on a `ProductCard` (in the listing/search/category page).
2. **Data source:** Call `GET /api/v1/products/{slug}` — where `slug` is already available on the product card from `ProductSummaryResponse.slug`.
3. **Reuse components:** The `VariantSelector` and `StockUrgencyIndicator` components **already exist** and accept `ProductDetailResponse` data. Drop them directly into the modal.
4. **Cart action:** Call `POST /api/v1/cart` with the selected `variantId` — same hook (`useAddToCart`) as the PDP.
5. **Rating display:** Read `avgRating` and `reviewCount` from the product card props (already in the listing state) rather than making a second API call.
6. **"View Full Details" CTA:** Navigate to `/products/{slug}` — already available.

### Data Flow

```
ProductCard (has slug, avgRating, reviewCount from listing API)
  → user clicks "Quick View"
  → Modal opens, calls GET /api/v1/products/{slug}
  → ProductDetailResponse returned (variants, images, urgency data, etc.)
  → VariantSelector renders color + size selection
  → StockUrgencyIndicator renders urgency message
  → "Add to Cart" button calls POST /api/v1/cart
  → "View Full Details" navigates to /products/{slug}
```

### Frontend Files to Create/Modify

| File | Action | Notes |
|---|---|---|
| `QuickViewModal.tsx` | Create | New modal component — wraps existing `VariantSelector`, `StockUrgencyIndicator`, `ImageGallery` |
| `ProductCard.tsx` | Modify | Add "Quick View" button trigger (hover reveal) |
| `useProductDetail.ts` (hook) | Reuse | Already exists — same hook used on PDP |
| `useAddToCart.ts` (hook) | Reuse | Already exists — no changes needed |
| `VariantSelector.tsx` | Reuse | No changes needed |
| `StockUrgencyIndicator.tsx` | Reuse | No changes needed |

### No New API Hooks Required

The existing `useProductDetail(slug)` hook in `useCatalog.ts` already calls `GET /api/v1/products/{slug}`. The Quick View Modal can reuse this hook directly, passing the `slug` from the product card. React Query will cache the response — if the user previously visited the PDP for this product, the Quick View is instant.

---

## 7. Summary

```
Backend status:     ✅ READY
New endpoints:      0 (zero)
DTO changes:        0 required (avgRating/reviewCount optional)
DB migrations:      0
API contracts:      Unchanged
Stock urgency:      Already implemented — reused for free
Cart integration:   Already supported
```

**Backend ready for Quick View implementation.**

The frontend team can proceed immediately using:
- `GET /api/v1/products/{slug}` — for all modal data
- `POST /api/v1/cart` — for Add to Cart
- Existing `VariantSelector` and `StockUrgencyIndicator` components
