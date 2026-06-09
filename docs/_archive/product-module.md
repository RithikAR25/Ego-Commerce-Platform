# Product Module — EGO E-Commerce Backend

> **Module:** `com.ego.raw_ego.catalog`
> **Phase:** 3 (Catalog Module)
> **Status:** ✅ Implemented
> **Depends on:** Phase 2 (Auth module — `com.ego.raw_ego.auth`)

All decisions conform to [product-architecture-decisions.md](../database/product-architecture-decisions.md).
Database schema defined in [product-schema.md](../database/product-schema.md).
E2E verification is documented in [catalog-api-swagger-testing-guide.md](./catalog-api-swagger-testing-guide.md).

---

## Package Structure

```
com.ego.raw_ego/
└── catalog/
    ├── enums/
    │   └── ProductStatus.java          ← DRAFT, ACTIVE, OUT_OF_STOCK, ARCHIVED
    │
    ├── entity/
    │   ├── Category.java               ← 2-level self-ref (parent_id nullable)
    │   ├── Product.java                ← base product + slug + status + tags
    │   ├── AttributeType.java          ← per-product: 'Color', 'Size'
    │   ├── AttributeValue.java         ← per-type: 'Black', 'M' — with .code for SKU
    │   ├── ProductVariant.java         ← SKU + price triple + is_active
    │   ├── VariantAttributeValue.java  ← join table (variant ↔ attribute values)
    │   ├── InventoryRecord.java        ← per-variant qty + optimistic lock
    │   ├── ProductImage.java           ← gallery-level images (Cloudinary public_id)
    │   └── VariantImage.java           ← variant-specific images + is_primary
    │
    ├── repository/
    │   ├── CategoryRepository.java
    │   ├── ProductRepository.java
    │   ├── AttributeTypeRepository.java
    │   ├── AttributeValueRepository.java
    │   ├── ProductVariantRepository.java
    │   ├── InventoryRecordRepository.java
    │   ├── ProductImageRepository.java
    │   └── VariantImageRepository.java
    │
    ├── dto/
    │   ├── request/
    │   │   ├── CreateCategoryRequest.java
    │   │   ├── UpdateCategoryRequest.java
    │   │   ├── CreateProductRequest.java
    │   │   ├── UpdateProductRequest.java
    │   │   ├── CreateVariantRequest.java
    │   │   ├── UpdateVariantRequest.java
    │   │   └── UpdateInventoryRequest.java
    │   └── response/
    │       ├── CategoryResponse.java   ← public (no internal fields)
    │       ├── CategoryTreeResponse.java ← root + subcategories list
    │       ├── ProductSummaryResponse.java ← for listing pages
    │       ├── ProductDetailResponse.java  ← for PDP (full variants + images)
    │       ├── VariantResponse.java
    │       ├── InventoryResponse.java  ← Admin only
    │       └── ImageResponse.java
    │
    ├── service/
    │   ├── CategoryService.java        ← CRUD + slug gen + depth validation
    │   ├── ProductService.java         ← CRUD + SKU gen + slug gen + status transitions
    │   ├── ProductVariantService.java  ← variant CRUD + SKU generation logic
    │   ├── InventoryService.java       ← reserve/release/commit + optimistic lock
    │   └── CloudinaryService.java      ← upload/delete + folder resolution
    │
    └── controller/
        ├── CategoryController.java     ← GET (public) + POST/PUT/DELETE (ADMIN)
        ├── ProductController.java      ← GET (public) + POST/PUT/DELETE (ADMIN)
        ├── ProductVariantController.java ← ADMIN only
        ├── InventoryController.java    ← ADMIN only
        └── ImageController.java        ← ADMIN only (Cloudinary upload)
```

---

## API Endpoints

### Category Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/categories` | Public | Get full category tree (root + subcategories) |
| `GET` | `/api/v1/categories/{slug}` | Public | Get single category + children |
| `POST` | `/api/v1/admin/categories` | ADMIN | Create category |
| `PUT` | `/api/v1/admin/categories/{id}` | ADMIN | Update category |
| `DELETE` | `/api/v1/admin/categories/{id}` | ADMIN | Soft-delete category |

### Product Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/products` | Public | List ACTIVE products (paginated, filtered) |
| `GET` | `/api/v1/products/{slug}` | Public | Get product detail (with all variants + images) |
| `GET` | `/api/v1/products/category/{categorySlug}` | Public | Products by subcategory |
| `GET` | `/api/v1/admin/products` | ADMIN | All products (all statuses) |
| `GET` | `/api/v1/admin/products/{slug}` | ADMIN | Product detail (any status) |
| `POST` | `/api/v1/admin/products` | ADMIN | Create product (DRAFT) |
| `PATCH` | `/api/v1/admin/products/{id}/status` | ADMIN | Change status |
| `DELETE` | `/api/v1/admin/products/{id}` | ADMIN | Soft-archive product |
| `DELETE` | `/api/v1/admin/products/{id}/permanent` | ADMIN | **Hard delete** — requires ARCHIVED + no order history |

### Variant Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/admin/products/{id}/variants` | ADMIN | Create variant (auto-generates SKU) |
| `PUT` | `/api/v1/admin/variants/{id}` | ADMIN | Update variant pricing |
| `DELETE` | `/api/v1/admin/variants/{id}` | ADMIN | Deactivate variant |

### Inventory Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/admin/inventory` | ADMIN | List all inventory (with low-stock filter) |
| `PUT` | `/api/v1/admin/inventory/{variantId}` | ADMIN | Set inventory quantity |

### Image Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/admin/products/{id}/images` | ADMIN | Upload gallery image to Cloudinary |
| `POST` | `/api/v1/admin/variants/{id}/images` | ADMIN | Upload variant image to Cloudinary |
| `DELETE` | `/api/v1/admin/images/{imageId}` | ADMIN | Delete image (Cloudinary + DB) |
| `PUT` | `/api/v1/admin/images/reorder` | ADMIN | Update display_order |

---

## SKU Generation

```java
// ProductVariantService.generateSku()
private String generateSku(Product product, AttributeValue colorValue, AttributeValue sizeValue) {
    String categoryCode = product.getCategory().getParent().getCode(); // root category
    String productCode  = product.getProductCode();
    String colorCode    = colorValue.getCode();   // e.g. "BLK"
    String sizeCode     = sizeValue.getCode();    // e.g. "M"

    String sku = String.format("EGO-%s-%s-%s-%s",
        categoryCode, productCode, colorCode, sizeCode);

    if (variantRepository.existsBySku(sku)) {
        throw new ConflictException("SKU already exists: " + sku);
    }
    return sku;
}
```

---

## Slug Generation

```java
// SlugUtils.java (in common/util)
public static String toSlug(String input) {
    return input.toLowerCase()
        .replace("&", "and")
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("\\s+", "-")
        .replaceAll("-{2,}", "-")
        .strip();
}

// With uniqueness enforcement:
public String generateUniqueSlug(String name, LongSupplier countBySlug) {
    String base = toSlug(name);
    String candidate = base;
    int suffix = 2;
    while (countBySlug.getAsLong(candidate) > 0) {
        candidate = base + "-" + suffix++;
    }
    return candidate;
}
```

---

## Cloudinary Integration

```java
// CloudinaryService.java
public String buildFolderPath(String productId, boolean isVariant, String variantId) {
    String env = cloudinaryProperties.getEnvironment(); // "dev" | "staging" | "prod"
    if (isVariant) {
        return String.format("ego/%s/products/%s/variants/%s", env, productId, variantId);
    }
    return String.format("ego/%s/products/%s/gallery", env, productId);
}

public ImageUploadResult uploadImage(MultipartFile file, String folder) {
    Map<String, Object> options = Map.of(
        "folder", folder,
        "resource_type", "image",
        "transformation", List.of(Map.of("quality", "auto", "fetch_format", "auto"))
    );
    // returns public_id + secure_url
}
```

---

## Product Status Transitions

```
Service enforces:

publishProduct(id):      DRAFT        → ACTIVE
archiveProduct(id):      ACTIVE|DRAFT → ARCHIVED
restoreProduct(id):      ARCHIVED     → DRAFT
hardDeleteProduct(id):   ARCHIVED     → [DELETED] (irreversible, 2 guards)

// Auto-triggered by InventoryService:
onStockDepleted(id):     ACTIVE       → OUT_OF_STOCK
onStockRestored(id):     OUT_OF_STOCK → ACTIVE
```

---

## Product Hard Delete (Two-Step Delete)

A product can only be permanently deleted after it has been **archived** (soft-deleted). This two-step safety model prevents accidental data loss.

### Endpoint

```
DELETE /api/v1/admin/products/{id}/permanent   →  204 No Content
```

### Guards (checked in order by `ProductService.hardDeleteProduct`)

| # | Guard | Error |
|---|-------|-------|
| 1 | `status == ARCHIVED` | 409 Conflict |
| 2 | `countOrderItemsByProductId(id) == 0` | 409 Conflict |

Order items are a **legal record** and must never be orphaned. The `order_items` table stores an immutable snapshot of every purchased line item. If any variant of the product has ever been ordered, permanent deletion is blocked.

### Cleanup Sequence (single `@Transactional`)

```
1. Load product → 404 if not found
2. Guard 1: status must be ARCHIVED
3. Guard 2: no order_items reference any variant of this product
4. SearchOutboxRepository.deleteByProductId(id)
   ↳ prevents outbox poller from processing stale ES events post-delete
5. ProductReviewRepository.deleteByProductId(id)
   ↳ productId is a raw FK column (no JPA cascade)
6. ProductRepository.deleteById(id)
   ↳ JPA CascadeType.ALL + orphanRemoval=true cascades to:
      └─ product_variants
         └─ variant_attribute_values (join table)
         └─ inventory_records
         └─ variant_images (Cloudinary assets NOT deleted — see note)
      └─ product_images (Cloudinary assets NOT deleted — see note)
      └─ attribute_types
         └─ attribute_values
```

> **Cloudinary Note:** Cloudinary assets (images) are **not** automatically purged during hard
> delete. This is intentional — purging requires an async Cloudinary API call that is not
> transactional. Image cleanup should be performed separately via the Cloudinary Admin API
> or a scheduled cleanup job (Phase 12).

### Repository Methods Added

| Repository | Method | Purpose |
|-----------|--------|---------|
| `ProductRepository` | `countOrderItemsByProductId(Long)` | Guard 2 — cross-module JPQL (same pattern as `ProductReviewRepository.hasUserPurchasedProduct`) |
| `ProductReviewRepository` | `deleteByProductId(Long)` | Cleanup reviews (raw FK, no JPA cascade) |
| `SearchOutboxRepository` | `deleteByProductId(Long)` | Purge stale ES outbox entries |

### JPQL Guard Query (cross-module)

```java
// ProductRepository
@Query("""
    SELECT COUNT(oi) FROM OrderItem oi
    WHERE oi.variantId IN (
        SELECT v.id FROM ProductVariant v WHERE v.product.id = :productId
    )
    """)
long countOrderItemsByProductId(@Param("productId") Long productId);
```

This bridges the cross-module boundary (`order` ↔ `catalog`) without importing entity classes directly. `OrderItem.variantId` is a raw `@Column(name = "variant_id")` field, accessed here by its JPA field name.

## Public Product List Query Parameters

| Param | Type | Example | Description |
|-------|------|---------|-------------|
| `category` | `String` | `oversized-tees` | Filter by subcategory slug |
| `minPrice` | `Integer` | `999` | Min selling price |
| `maxPrice` | `Integer` | `2999` | Max selling price |
| `colors` | `String[]` | `BLK,WHT` | Filter by color codes |
| `sizes` | `String[]` | `M,L,XL` | Filter by size codes |
| `sortBy` | `String` | `price_asc`, `newest`, `popular` | Sort order |
| `page` | `Integer` | `0` | Page number (0-indexed) |
| `size` | `Integer` | `24` | Page size |

---

## Response: ProductDetailResponse

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Oversized Acid-Wash Tee",
    "slug": "oversized-acid-wash-tee",
    "description": "...",
    "material": "100% Cotton",
    "status": "ACTIVE",
    "tags": ["streetwear", "oversized"],
    "category": {
      "id": 3,
      "name": "Oversized Tees",
      "slug": "oversized-tees",
      "parent": { "id": 1, "name": "Men", "slug": "men" }
    },
    "attributeTypes": [
      {
        "name": "Color",
        "values": [
          { "id": 1, "value": "Black", "code": "BLK", "hexColor": "#1A1A1A" },
          { "id": 2, "value": "White", "code": "WHT", "hexColor": "#FFFFFF" }
        ]
      },
      {
        "name": "Size",
        "values": [
          { "id": 3, "value": "S",  "code": "S" },
          { "id": 4, "value": "M",  "code": "M" },
          { "id": 5, "value": "L",  "code": "L" },
          { "id": 6, "value": "XL", "code": "XL" }
        ]
      }
    ],
    "variants": [
      {
        "id": 1,
        "sku": "EGO-TEE-0001-BLK-M",
        "price": 1299.00,
        "compareAtPrice": 1799.00,
        "discountPercent": 28,
        "isActive": true,
        "stockStatus": "IN_STOCK",
        "attributeValues": [
          { "typeId": 1, "typeName": "Color", "valueId": 1, "value": "Black", "code": "BLK" },
          { "typeId": 2, "typeName": "Size",  "valueId": 4, "value": "M",     "code": "M"   }
        ],
        "images": [
          {
            "id": 1,
            "url": "https://res.cloudinary.com/ego/image/upload/ego/prod/products/1/variants/1/hero",
            "isPrimary": true,
            "displayOrder": 0
          }
        ]
      }
    ],
    "galleryImages": [
      {
        "id": 10,
        "url": "https://res.cloudinary.com/ego/image/upload/ego/prod/products/1/gallery/lifestyle_1",
        "displayOrder": 0
      }
    ]
  }
}
```

---

## Security: Field Visibility

| Field | Public API | Admin API |
|-------|-----------|-----------|
| `price` | ✅ | ✅ |
| `compareAtPrice` | ✅ | ✅ |
| `discountPercent` (computed) | ✅ | ✅ |
| `costPrice` | ❌ **NEVER** | ✅ |
| `quantity_available` | ✅ (as `stockStatus` label) | ✅ (as exact number) |
| `quantity_reserved` | ❌ | ✅ |
| `low_stock_threshold` | ❌ | ✅ |

---

## Error Codes

| HTTP | Scenario |
|------|----------|
| 400 | Invalid request body, SKU format error, category depth violation |
| 404 | Product/Category/Variant not found by slug or id |
| 409 | Slug already exists, SKU collision, hard-delete guard failure (not ARCHIVED or has order history) |
| 422 | Cannot archive product with active orders, cannot delete non-empty category |
| 500 | Cloudinary upload failure, unexpected server error |

---

## Phase Dependencies

| Feature | Required Phase |
|---------|---------------|
| Elasticsearch product search | Phase 4 |
| Cart (reserve/release inventory) | Phase 5 |
| Stock depletion auto-status | Phase 5 |
| Email low-stock alerts | Phase 8 (SendGrid) |
| Product reviews | Phase 9 |
| Admin dashboard product CRUD UI | Phase 11 |
