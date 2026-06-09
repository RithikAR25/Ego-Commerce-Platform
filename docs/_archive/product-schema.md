# Product Schema — EGO E-Commerce Platform

> **Module:** `com.ego.raw_ego.catalog`
> **Phase:** 3 (Catalog Module)
> **Status:** 🔒 Schema Locked
> **DDL Strategy:** JPA `ddl-auto=update` (Flyway in Phase 14)

All schema decisions conform to [product-architecture-decisions.md](./product-architecture-decisions.md).

---

## Entity Relationship Diagram

```
categories (root)
    │ 1:N (parent_id self-ref, max depth = 1)
    ▼
categories (subcategory)
    │ 1:N
    ▼
products ──────────────────────────────────────────┐
    │ 1:N                                           │ 1:N
    ▼                                               ▼
product_attribute_types                       product_images
    │ 1:N                                     (gallery-level)
    ▼
product_attribute_values
    │ N:M (via variant_attribute_values)
    ▼
product_variants ──────────────────────────────────┐
    │ 1:1                                           │ 1:N
    ▼                                               ▼
inventory_records                            variant_images
                                             (variant-level)
```

---

## Table: `categories`

```sql
CREATE TABLE categories (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id       BIGINT UNSIGNED DEFAULT NULL,            -- NULL = root category
    name            VARCHAR(100)    NOT NULL,
    code            VARCHAR(10)     NOT NULL,                -- e.g. 'MEN', 'WOM', 'ACC'
    slug            VARCHAR(150)    NOT NULL,                -- e.g. 'men', 'oversized-tees'
    description     TEXT            DEFAULT NULL,
    image_url       VARCHAR(500)    DEFAULT NULL,
    display_order   INT             NOT NULL DEFAULT 0,
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uq_category_slug   (slug),
    UNIQUE KEY uq_category_code   (code),
    KEY idx_category_parent       (parent_id),

    CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Notes

- `parent_id = NULL` → root category (e.g. "Men", "Women")
- `parent_id IS NOT NULL` → subcategory (e.g. "Oversized Tees")
- Max depth enforced at application layer — subcategory's `parent_id` must always point to a root
- `code` used in SKU generation (first segment after `EGO-`)

---

## Table: `products`

```sql
CREATE TABLE products (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id         BIGINT UNSIGNED NOT NULL,            -- FK to subcategory
    name                VARCHAR(255)    NOT NULL,
    product_code        VARCHAR(10)     NOT NULL,            -- e.g. '0001' — part of SKU
    slug                VARCHAR(300)    NOT NULL,            -- e.g. 'oversized-acid-wash-tee'
    description         TEXT            DEFAULT NULL,
    material            VARCHAR(255)    DEFAULT NULL,        -- e.g. '100% Cotton'
    care_instructions   TEXT            DEFAULT NULL,
    status              ENUM('DRAFT','ACTIVE','OUT_OF_STOCK','ARCHIVED')
                                        NOT NULL DEFAULT 'DRAFT',
    tags                JSON            DEFAULT NULL,        -- e.g. ["streetwear","oversized"]
    version             INT UNSIGNED    NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uq_product_slug          (slug),
    UNIQUE KEY uq_product_code          (product_code),
    KEY idx_product_category            (category_id),
    KEY idx_product_status              (status),

    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Notes

- `product_code` is a zero-padded auto-sequence (e.g. `0001`, `0042`) used in SKU generation
- `status` transitions controlled at service layer (see architecture decisions §7)
- `tags` stored as JSON for Elasticsearch indexing flexibility
- `version` used for optimistic locking on admin concurrent edits

---

## Table: `product_attribute_types`

```sql
CREATE TABLE product_attribute_types (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id      BIGINT UNSIGNED NOT NULL,
    name            VARCHAR(50)     NOT NULL,                -- e.g. 'Color', 'Size'
    display_order   INT             NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_attr_type_product (product_id),

    CONSTRAINT fk_attr_type_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: `product_attribute_values`

```sql
CREATE TABLE product_attribute_values (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attribute_type_id   BIGINT UNSIGNED NOT NULL,
    value               VARCHAR(100)    NOT NULL,            -- e.g. 'Black', 'M'
    code                VARCHAR(10)     NOT NULL,            -- e.g. 'BLK', 'M' — used in SKU
    display_order       INT             NOT NULL DEFAULT 0,
    hex_color           VARCHAR(7)      DEFAULT NULL,        -- '#1A1A1A' — for color swatches
    swatch_image_url    VARCHAR(500)    DEFAULT NULL,        -- for pattern swatches

    PRIMARY KEY (id),
    KEY idx_attr_value_type (attribute_type_id),

    CONSTRAINT fk_attr_value_type
        FOREIGN KEY (attribute_type_id) REFERENCES product_attribute_types (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: `product_variants`

```sql
CREATE TABLE product_variants (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id          BIGINT UNSIGNED NOT NULL,
    sku                 VARCHAR(50)     NOT NULL,            -- e.g. 'EGO-TEE-0001-BLK-M'
    price               DECIMAL(10,2)   NOT NULL,            -- final selling price
    compare_at_price    DECIMAL(10,2)   DEFAULT NULL,        -- crossed-out price for discount display
    cost_price          DECIMAL(10,2)   DEFAULT NULL,        -- internal COGS (never exposed publicly)
    weight_grams        INT             DEFAULT NULL,        -- for shipping calculation
    is_active           TINYINT(1)      NOT NULL DEFAULT 1,
    version             INT UNSIGNED    NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uq_variant_sku   (sku),
    KEY idx_variant_product     (product_id),

    CONSTRAINT fk_variant_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### SKU Generation Logic (Service Layer)

```java
// Pseudocode — implemented in ProductVariantService
String sku = String.format("EGO-%s-%s-%s-%s",
    category.getCode(),         // e.g. "TEE"
    product.getProductCode(),   // e.g. "0001"
    colorAttributeValue.getCode(), // e.g. "BLK"
    sizeAttributeValue.getCode()   // e.g. "M"
);
// Validate uniqueness before persisting
```

---

## Table: `variant_attribute_values`

```sql
CREATE TABLE variant_attribute_values (
    variant_id          BIGINT UNSIGNED NOT NULL,
    attribute_value_id  BIGINT UNSIGNED NOT NULL,

    PRIMARY KEY (variant_id, attribute_value_id),

    CONSTRAINT fk_vav_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE,
    CONSTRAINT fk_vav_attr_value
        FOREIGN KEY (attribute_value_id) REFERENCES product_attribute_values (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: `inventory_records`

```sql
CREATE TABLE inventory_records (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    variant_id          BIGINT UNSIGNED NOT NULL,
    quantity_available  INT             NOT NULL DEFAULT 0,
    quantity_reserved   INT             NOT NULL DEFAULT 0,
    low_stock_threshold INT             NOT NULL DEFAULT 5,
    version             INT UNSIGNED    NOT NULL DEFAULT 0,  -- optimistic lock
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uq_inventory_variant (variant_id),
    KEY idx_inventory_qty           (quantity_available),

    CONSTRAINT chk_qty_available CHECK (quantity_available >= 0),
    CONSTRAINT chk_qty_reserved  CHECK (quantity_reserved >= 0),

    CONSTRAINT fk_inventory_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: `product_images`

```sql
CREATE TABLE product_images (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id          BIGINT UNSIGNED NOT NULL,
    cloudinary_public_id VARCHAR(300)   NOT NULL,            -- e.g. 'ego/prod/products/1/gallery/img_abc'
    url                 VARCHAR(500)    NOT NULL,            -- full Cloudinary URL (original)
    alt_text            VARCHAR(255)    DEFAULT NULL,
    display_order       INT             NOT NULL DEFAULT 0,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_product_image_product (product_id),

    CONSTRAINT fk_product_image_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Table: `variant_images`

```sql
CREATE TABLE variant_images (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    variant_id          BIGINT UNSIGNED NOT NULL,
    cloudinary_public_id VARCHAR(300)   NOT NULL,            -- e.g. 'ego/prod/products/1/variants/7/hero'
    url                 VARCHAR(500)    NOT NULL,
    alt_text            VARCHAR(255)    DEFAULT NULL,
    is_primary          TINYINT(1)      NOT NULL DEFAULT 0,  -- hero image for this variant
    display_order       INT             NOT NULL DEFAULT 0,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_variant_image_variant (variant_id),

    CONSTRAINT fk_variant_image_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Index Summary

| Table | Index | Columns | Purpose |
|-------|-------|---------|---------|
| `categories` | `uq_category_slug` | `slug` | Storefront URL lookup |
| `categories` | `uq_category_code` | `code` | SKU generation lookup |
| `categories` | `idx_category_parent` | `parent_id` | Fetch subcategories |
| `products` | `uq_product_slug` | `slug` | Storefront URL routing |
| `products` | `uq_product_code` | `product_code` | SKU generation uniqueness |
| `products` | `idx_product_category` | `category_id` | Category listing page |
| `products` | `idx_product_status` | `status` | Public filter (ACTIVE only) |
| `product_variants` | `uq_variant_sku` | `sku` | Cart / Order item lookup |
| `product_variants` | `idx_variant_product` | `product_id` | Variant listing per product |
| `inventory_records` | `uq_inventory_variant` | `variant_id` | 1:1 enforcement |
| `inventory_records` | `idx_inventory_qty` | `quantity_available` | Low-stock queries |

---

## Full Entity Graph

```
categories (root: parent_id=NULL)
    │ id → parent_id
    └── categories (sub: parent_id=rootId)
            │ id → category_id
            └── products
                    │ id → product_id
                    ├── product_attribute_types
                    │       │ id → attribute_type_id
                    │       └── product_attribute_values ──────┐
                    │                                           │ id → attribute_value_id
                    ├── product_variants                        │
                    │       │ id → variant_id                   │
                    │       ├── variant_attribute_values ───────┘
                    │       ├── inventory_records (1:1)
                    │       └── variant_images
                    └── product_images
```
