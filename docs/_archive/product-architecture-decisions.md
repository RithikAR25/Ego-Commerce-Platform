# Product Architecture Decisions — EGO E-Commerce Platform

> **Status:** 🔒 LOCKED — Do not modify without team sign-off
> **Phase:** 3 (Catalog Module)
> **Last Updated:** 2026-05-22

These decisions are the **source of truth** for all catalog-related backend implementation, database schema, and frontend product flows. Every future implementation must conform to these decisions.

---

## 1. SKU Format

### Decision

```
EGO-{CATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE}
```

### Examples

| Variant | SKU |
|---------|-----|
| Tee / Black / M | `EGO-TEE-0001-BLK-M` |
| Hoodie / White / XL | `EGO-HOD-0002-WHT-XL` |
| Cargo / Olive / L | `EGO-CGO-0003-OLV-L` |

### Segment Rules

| Segment | Format | Source | Example |
|---------|--------|--------|---------|
| `EGO` | Literal prefix | Hardcoded | `EGO` |
| `CATEGORY_CODE` | 3-letter uppercase | Category.code | `TEE`, `HOD`, `CGO` |
| `PRODUCT_CODE` | 4-digit zero-padded | Product.productCode | `0001`, `0042` |
| `COLOR_CODE` | 3-letter uppercase | AttributeValue.code | `BLK`, `WHT`, `OLV`, `RED` |
| `SIZE` | Uppercase | AttributeValue.code | `XS`, `S`, `M`, `L`, `XL`, `XXL` |

### Enforcement Rules

- ✅ SKU is **unique** — enforced by `UNIQUE INDEX` in DB
- ✅ SKU is **immutable** — never updated after variant creation
- ✅ SKU is **generated server-side** at variant creation time
- ✅ SKU is **indexed** for fast lookup in cart, orders, and admin
- ❌ SKU is **never** client-generated or user-supplied

---

## 2. Category Structure

### Decision

**2-level only: Category → Subcategory**

```
Category (depth = 0)
    └── Subcategory (depth = 1)
```

### Examples

| Category | Subcategory |
|----------|-------------|
| Men | Oversized Tees |
| Men | Hoodies |
| Men | Cargo Pants |
| Women | Crop Tops |
| Women | Co-ords |
| Accessories | Caps |

### Rationale

- Simpler frontend navigation — no recursive tree rendering
- Simpler filtering — flat JOIN instead of recursive CTE
- Better SEO — clean URL paths (`/men/oversized-tees`)
- Easier admin management — no tree drag-and-drop needed
- Better performance — no recursive queries
- Aligns with modern fashion e-commerce UX (Snitch, Bewakoof, H&M)

### Enforcement Rules

- ✅ `categories` table has a nullable `parent_id` FK to itself
- ✅ Root categories have `parent_id = NULL`
- ✅ Subcategories have `parent_id` pointing to a root category
- ❌ `parent_id` of a subcategory must **never** point to another subcategory (depth capped at 1)
- ❌ **Do not implement** unlimited recursive category trees

---

## 3. Price Model

### Decision

**Variant-level 3-field pricing: `price`, `compare_at_price`, `cost_price`**

### Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `price` | `DECIMAL(10,2)` | Final selling price (shown to customer) | `1299.00` |
| `compare_at_price` | `DECIMAL(10,2) NULLABLE` | Original/crossed-out price for discount display | `1799.00` |
| `cost_price` | `DECIMAL(10,2) NULLABLE` | Internal cost / COGS (admin only, never exposed) | `600.00` |

### Discount Display Logic

```
If compare_at_price IS NOT NULL AND compare_at_price > price:
    discount_percent = Math.round((compare_at_price - price) / compare_at_price * 100)
    display: "₹1,299  ~~₹1,799~~  (28% off)"
Else:
    display: "₹1,299"
```

### Enforcement Rules

- ✅ `price` is the **only** field used as the final selling price
- ✅ Discounts are **calculated dynamically** from `compare_at_price` vs `price`
- ✅ `cost_price` is **never** exposed in any public API response
- ✅ Future coupon/promo codes reduce from `price` at checkout time
- ❌ **Do not** implement a global product-level discount percentage column
- ❌ **Do not** store pre-computed discount percentages in the DB

---

## 4. Inventory Model

### Decision

**Per-variant inventory tracking — one inventory row per variant.**

### Structure

```
ProductVariant: "Oversized Acid-Wash Tee / Black / M"  → InventoryRecord (qty: 12)
ProductVariant: "Oversized Acid-Wash Tee / Black / L"  → InventoryRecord (qty: 5)
ProductVariant: "Oversized Acid-Wash Tee / White / M"  → InventoryRecord (qty: 0)  ← OUT_OF_STOCK
```

### Inventory Fields

| Field | Purpose |
|-------|---------|
| `quantity_available` | Units available for purchase |
| `quantity_reserved` | Units held in active carts (not yet ordered) |
| `low_stock_threshold` | Alert threshold (default: 5) |
| `version` | Optimistic lock — prevents overselling on concurrent requests |

### Reservation Lifecycle

```
ADD TO CART       → quantity_reserved += 1
REMOVE FROM CART  → quantity_reserved -= 1
PLACE ORDER       → quantity_available -= 1, quantity_reserved -= 1
CANCEL ORDER      → quantity_available += 1
RESERVE EXPIRY    → quantity_reserved -= 1  (Redis TTL triggers cleanup)
```

### Enforcement Rules

- ✅ Every `product_variants` row has exactly one `inventory_records` row
- ✅ `quantity_available` has `CHECK (quantity_available >= 0)` constraint
- ✅ Inventory uses optimistic locking (`version` column)
- ❌ **Never** track inventory at product level
- ❌ **Never** allow `quantity_available` to go negative

---

## 5. Cloudinary Folder Structure

### Decision

```
ego/{environment}/products/{productId}/
```

### Extended Structure

```
ego/
├── dev/
│   └── products/
│       └── {productId}/
│           ├── original/     ← high-res originals uploaded by admin
│           ├── gallery/      ← product-level gallery images (all variants)
│           └── variants/
│               └── {variantId}/  ← variant-specific images
└── prod/
    └── products/
        └── {productId}/
            ├── original/
            ├── gallery/
            └── variants/
                └── {variantId}/
```

### Environment Values

| Environment | Folder Prefix |
|-------------|---------------|
| Development | `ego/dev/` |
| Staging | `ego/staging/` |
| Production | `ego/prod/` |

### Cloudinary Transformation Rules

| Transformation | Usage |
|----------------|-------|
| `w_800,h_1000,c_fill,g_center,f_auto,q_auto` | Product card thumbnail |
| `w_1200,h_1500,c_fill,g_center,f_auto,q_auto` | Product detail view |
| `w_80,h_100,c_fill,g_center,f_auto,q_auto` | Cart / mini thumbnail |
| `f_auto,q_auto` | Original with CDN optimization only |

### Enforcement Rules

- ✅ Environment is injected from `application.properties` — not hardcoded
- ✅ `productId` in folder path is the DB `id` (Long)
- ✅ `variantId` folder is used for variant-specific images
- ✅ All images support responsive transformations via Cloudinary URL params
- ❌ Never store transformed URLs — always store original Cloudinary public_id

---

## 6. Product URL Slugs

### Decision

**Unique, SEO-friendly slugs generated server-side from product name.**

### Generation Rules

```
"Oversized Acid-Wash Tee"  →  "oversized-acid-wash-tee"
"Cargo Pants (Olive)"      →  "cargo-pants-olive"
"Men's Classic Hoodie"     →  "mens-classic-hoodie"
```

Algorithm:
1. Lowercase
2. Replace `&` with `and`
3. Remove special characters (except `-`)
4. Replace whitespace with `-`
5. Remove apostrophes and parentheses
6. Deduplicate consecutive dashes
7. If slug already exists → append `-2`, `-3`, etc.

### Enforcement Rules

- ✅ Slug is `UNIQUE` indexed in DB
- ✅ Slug is **generated server-side** — never user-supplied directly
- ✅ Slug is used in frontend routes: `/products/{slug}`
- ✅ Old slugs **must never** return 404 — implement redirect table (Phase 11)
- ❌ Never use product `id` as the primary URL identifier for public routes

---

## 7. Product Status

### Decision

```java
public enum ProductStatus {
    DRAFT,         // being created/edited, not visible to customers
    ACTIVE,        // live and purchasable
    OUT_OF_STOCK,  // all variants have quantity_available = 0
    ARCHIVED       // permanently hidden, no longer sold
}
```

### Transition Rules

```
DRAFT     → ACTIVE         (admin publishes)
ACTIVE    → OUT_OF_STOCK   (automatic — triggered when last variant hits 0 stock)
OUT_OF_STOCK → ACTIVE      (automatic — triggered when any variant gets restocked)
ACTIVE    → ARCHIVED       (admin archives)
DRAFT     → ARCHIVED       (admin discards)
ARCHIVED  → DRAFT          (admin restores to edit)
```

### Public API Visibility

| Status | Visible in Storefront | Visible in Admin |
|--------|----------------------|-----------------|
| DRAFT | ❌ | ✅ |
| ACTIVE | ✅ | ✅ |
| OUT_OF_STOCK | ✅ (with badge) | ✅ |
| ARCHIVED | ❌ | ✅ |

---

## 8. Variant Image Support

### Decision

**Both product-level gallery images and variant-specific images are supported.**

### Image Types

| Type | Location | Purpose |
|------|----------|---------|
| Gallery image | `products/{productId}/gallery/` | Lifestyle shots, detail shots (not color-specific) |
| Variant image | `products/{productId}/variants/{variantId}/` | Color-specific images |

### Frontend Behavior

```
User lands on product page:
  → Load default variant images (first ACTIVE variant)
  → Display gallery images below

User selects "Red" color swatch:
  → Fetch variant images for Red variant
  → Replace main image + thumbnail strip with Red-specific images
  → Gallery images remain unchanged

User selects "M" size:
  → No image change (sizes don't have separate images)
  → Resolve to specific variantId for Add to Cart
```

### Enforcement Rules

- ✅ `product_images` table stores gallery-level images
- ✅ `variant_images` table stores variant-level images (linked to `variant_id`)
- ✅ Images have `display_order` for manual ordering control
- ✅ `is_primary` flag marks the hero image for each variant
- ❌ Never use a single image URL column on the variant — always normalize into an images table

---

## Summary Checklist

| Decision | Status |
|----------|--------|
| SKU format: `EGO-{CAT}-{PROD}-{COLOR}-{SIZE}` | 🔒 Locked |
| Category depth: 2-level only | 🔒 Locked |
| Price model: `price` + `compare_at_price` + `cost_price` per variant | 🔒 Locked |
| Inventory: per-variant with optimistic lock | 🔒 Locked |
| Cloudinary: `ego/{env}/products/{id}/` with variant subfolder | 🔒 Locked |
| Slugs: unique, SEO-friendly, server-generated | 🔒 Locked |
| Status enum: DRAFT / ACTIVE / OUT_OF_STOCK / ARCHIVED | 🔒 Locked |
| Variant images: separate `variant_images` table | 🔒 Locked |
