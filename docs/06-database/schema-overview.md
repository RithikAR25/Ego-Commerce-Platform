# Database Schema Overview

> **Source of Truth:** MySQL 8 is the primary data store. Elasticsearch is a read-only index derived from MySQL. Redis is used for ephemeral data (cart, sessions).

## Database Architecture

The EGO platform uses a single relational database (`rawego`) partitioned logically by feature modules. All tables use InnoDB engine and UTF-8 encoding.

### Core Schemas (`docs/06-database/`)

The full SQL definitions are maintained in the following files:

| File | Sub-Domain | Description |
|---|---|---|
| `schema_v2.sql` | Core | Base tables: Users, Products, Categories, Attributes |
| `schema_category_hierarchy_links.sql` | Catalog | Closure table for 3-level category hierarchy |
| `schema_order_module.sql` | Orders | Orders, OrderItems, OrderStatusHistory |
| `schema_return_module.sql` | Returns | Return requests, Return items, Return photos |
| `schema_coupons.sql` | Checkout | Coupons and usage tracking |
| `schema_wishlist_items.sql` | Catalog | User wishlists (variant-level) |
| `schema_product_reviews.sql` | Catalog | Purchase-gated product reviews |
| `schema_notification_logs.sql` | Notifications | SendGrid email dispatch logs |
| `schema_razorpay_columns.sql` | Payments | Razorpay order IDs and webhook signatures |

## Key Architectural Patterns

### 1. Soft Deletes (`is_deleted`)
Entities like `products` and `users` use an `is_deleted` boolean instead of hard deletion.
Hibernate is configured with `@SQLRestriction("is_deleted = false")` on the `Product` entity, meaning soft-deleted products automatically disappear from all queries, even JOINs, without manual `WHERE` clauses.

### 2. The Outbox Pattern (`search_outbox`)
To keep Elasticsearch in sync without distributed transactions, the catalog module writes to `search_outbox` in the exact same MySQL transaction that updates a product. A background poller reads this table and upserts to Elasticsearch.
See: [ADR-002](../13-decisions/architecture-decision-records/ADR-002-search-outbox-pattern.md)

### 3. Entity Attribute Value (EAV) Model
To avoid sparse columns for product details (Color, Size, Material), an EAV model is used:
- `product_attribute_types` (Dimensions: e.g. "Size")
- `product_attribute_values` (Values: e.g. "XL")
- `variant_attribute_values` (Junction table linking a Variant to its Values)
See: [ADR-004](../13-decisions/architecture-decision-records/ADR-004-product-attribute-model.md)

### 4. Closure Table (Category Hierarchy)
Categories support infinite depth but are strictly governed to a 3-level tree (Gender → Category → Subcategory) via `category_hierarchy_links`. This table maps every category to all of its descendants, allowing single-query subtree fetching.
See: [ADR-001](../13-decisions/architecture-decision-records/ADR-001-category-architecture.md)

### 5. Optimistic Locking (`version`)
High-concurrency tables like `inventory_records` use a `version` BIGINT column. Hibernate automatically increments this on update. If two checkouts try to grab the last unit simultaneously, one will throw an `OptimisticLockException`, preventing overselling.

## Migrating Data

For local development, use the seed scripts in order:
1. `schema_v2.sql` (Base tables)
2. `01_category_seed.sql` (Hierarchy)
3. `02_product_seed.sql` (Products, variants, images)

*Note: In Phase 15, Flyway or Liquibase will be introduced to replace manual `.sql` script execution.*
