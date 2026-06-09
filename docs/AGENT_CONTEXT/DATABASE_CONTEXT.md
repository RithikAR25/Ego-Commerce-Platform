# Database Context & Schema Decisions — EGO Platform

This document serves as the database context, schema mapping, and entity relationship reference for the EGO platform.

---

## 1. Entity Relationship Diagram (ERD)

The EGO database is designed with high normalization to support clean variant separations and audit paths.

```
 categories (Root Category parent_id = NULL)
     │
     └── categories (Subcategory parent_id pointing to Root)
             │
             └── products (Product Concept: base descriptions, tags, material)
                     │
                     ├── product_attribute_types (e.g. 'Color', 'Size')
                     │       │
                     │       └── product_attribute_values (e.g. 'Black', 'M', swatches)
                     │               │
                     │               │ N:M (Mapped via variant_attribute_values)
                     │               ▼
                     └── product_variants (SKUs: prices, weights, status)
                             │
                             ├── inventory_records (1:1 Stock records, optimistic locks)
                             │
                             └── variant_images (Variant-specific photos)
```

---

## 2. Table Schemas & Relational Mapping

### Categories (`categories`)
Supports a flat 2-level hierarchy:
* `parent_id`: FK to `categories.id`. Nullable. If `NULL` → represents root category (e.g., "Men"). If set → represents subcategory (e.g., "Hoodies").
* **Depth Constraint:** Enforced via application logic to prevent depth from exceeding 1.

### Products (`products`)
Stores the high-level base product metadata:
* `category_id`: Must always point to a **subcategory** (depth = 1), never a root category.
* `product_code`: Zero-padded 4-digit sequence (e.g. `0042`) used as a static identifier in SKU compilation.
* `status`: Enum mapping (`DRAFT`, `ACTIVE`, `OUT_OF_STOCK`, `ARCHIVED`).
* `tags`: JSON column in MySQL containing string arrays (e.g., `["streetwear", "heavyweight"]`) to support indexing in Elasticsearch.

### Product Attribute Types (`product_attribute_types`)
Defines the dimensions/axes of product options (e.g., "Color", "Size").
* `product_id`: FK to `products.id`.

### Product Attribute Values (`product_attribute_values`)
Defines the concrete options under each attribute axis:
* `attribute_type_id`: FK to `product_attribute_types.id`.
* `value`: Readable string (e.g. "Olive", "XL").
* `code`: Static uppercase code used in SKU generation (e.g. "OLV", "XL").
* `hex_color`: Hex color code (`#6B7B3A`) for UI swatches.

### Product Variants (`product_variants`)
Represents the concrete, purchasable units:
* `sku`: Auto-generated, unique, and immutable string: `EGO-{CAT_CODE}-{PROD_CODE}-{COLOR_CODE}-{SIZE_CODE}` (e.g., `EGO-MEN-0001-OLV-M`).
* `price`: The final storefront selling price.
* `compare_at_price`: The original price to calculate discounts.
* `cost_price`: Admin-only COGS.

### Variant Attribute Values (`variant_attribute_values`)
Join table resolving the N:M relationship between variants and attribute values.
* Composite primary key: `(variant_id, attribute_value_id)`.

### Inventory Records (`inventory_records`)
1:1 stock ledger for variants:
* `variant_id`: Unique FK to `product_variants.id`.
* `quantity_available`: Units physically available for purchase.
* `quantity_reserved`: Units temporarily locked in active carts.
* `version`: Version integer used for JPA **optimistic locking** to prevent overselling on concurrent checkout requests.

---

## 3. High-Performance Indexing Strategy

To guarantee fast query speeds on the storefront, indices are established on critical query paths:

| Table | Index Name | Columns | Operational Purpose |
|---|---|---|---|
| `categories` | `uq_category_slug` | `slug` (Unique) | Storefront category routing lookup |
| `categories` | `uq_category_code` | `code` (Unique) | Uniqueness validation for SKU builders |
| `products` | `uq_product_slug` | `slug` (Unique) | Storefront product detail routing lookup |
| `products` | `uq_product_code` | `product_code` (Unique) | Uniqueness verification for product codes |
| `products` | `idx_product_category` | `category_id` | Speeds up product listing queries under categories |
| `products` | `idx_product_status` | `status` | Filters active products for storefront listing queries |
| `product_variants` | `uq_variant_sku` | `sku` (Unique) | Cart resolution and administrative catalog updates |
| `inventory_records` | `uq_inventory_variant` | `variant_id` (Unique) | Enforces 1:1 inventory isolation constraints |
| `inventory_records` | `idx_inventory_qty` | `quantity_available` | Optimizes low-stock notification sweeps |

---

## 4. Key Relational Constraints & Audit Systems

* **Foreign Key Cascade Deletions:**
  * Deleting a `Product` automatically cascade-deletes its `product_attribute_types` and `product_variants` via database-level foreign key cascades (`ON DELETE CASCADE`).
  * Deleting a `ProductVariant` cascade-deletes its corresponding `inventory_records` and `variant_images`.
  * Category deletion enforces `ON DELETE RESTRICT` to prevent orphan category trees. A category cannot be deleted if active products are mapped to it.

* **Database Constraints:**
  * `quantity_available >= 0` check constraint.
  * `quantity_reserved >= 0` check constraint.

* **Audit Fields:**
  * Primary tables carry `created_at` and `updated_at` timestamps managed automatically via Hibernate annotations (`@CreationTimestamp`, `@UpdateTimestamp`).
