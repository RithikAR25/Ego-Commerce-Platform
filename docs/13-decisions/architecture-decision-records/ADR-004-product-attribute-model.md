# ADR-004: Product Attribute Model — Entity-Attribute-Value (EAV)

**Status:** Accepted  
**Date:** May 2026  
**Decision-makers:** Engineering Team  
**Implemented by:** Antigravity AI Agent

---

## Context

Fashion products have variable dimensions. A T-shirt may have Color × Size attributes. Shoes may have Color × UK Size × Width. Accessories may have Color only. A rigid relational schema (`color VARCHAR, size VARCHAR` columns on `product_variants`) would:
- Require schema changes for every new attribute type
- Leave unused columns for product types that don't have certain attributes
- Prevent admins from defining new dimension types without code changes

The EGO platform targets a fashion brand that needs a flexible, admin-configurable attribute system.

---

## Decision

**Entity-Attribute-Value (EAV) model with fixed-schema variant table**

### Table Structure

```sql
-- Attribute types (admin-defined dimensions)
product_attribute_types:
  id, name (e.g. "Color", "Size", "Width"), created_at

-- Attribute values (admin-defined options for each type)
product_attribute_values:
  id, attribute_type_id FK, value (e.g. "Black", "XL"), hex_color

-- Variants (one row per combination)
product_variants:
  id, product_id FK, sku, price, compare_at_price, is_active

-- Junction: which values apply to which variant
variant_attribute_values:
  variant_id FK, attribute_value_id FK
  PRIMARY KEY (variant_id, attribute_value_id)
```

### SKU Auto-Generation
SKU format: `EGO-{CATEGORY_CODE}-{SEQUENCE}-{COLOR_VALUE}-{SIZE_VALUE}`  
Example: `EGO-TEE-0001-BLK-M`

- `CATEGORY_CODE` = LEAF category's `code` field (immutable after creation)
- `SEQUENCE` = zero-padded 4-digit product sequence within category
- Attribute values with type "Color" and "Size" are extracted and abbreviated

### Invariants Enforced at Service Layer
- Product must have at least one variant before going ACTIVE
- Variant's attribute combination must be unique within a product (no two variants with identical Color+Size)
- `hex_color` on Color attribute values enables frontend swatch rendering without hardcoded mapping

---

## Alternatives Considered

### A. Fixed columns (`color VARCHAR, size VARCHAR` on variants)
- **Rejected:** Cannot support dynamic attribute types without migration. Cannot support attributes like "Width" or "Material" without schema changes. Admin cannot self-serve new dimension types.

### B. JSON column (`attributes JSONB` on variants)
- **Evaluated:** Simpler to query for a single variant. However: no referential integrity, cannot facet-filter in ES without denormalization, admin cannot manage attribute types as first-class entities.
- **Rejected** in favor of normalized EAV + denormalized ES document

### C. JSONB + separate admin-defined schema
- **Rejected:** Hybrid approach adds complexity without meaningful benefit over EAV for a 2-3 dimension fashion attribute model.

### D. Polymorphic variant table (separate tables per product type)
- **Rejected:** Over-engineering for a fashion brand with predictable attribute patterns (Color, Size cover 95% of cases).

---

## Trade-offs and Mitigations

| EAV Trade-off | Mitigation |
|---|---|
| N+1 queries when loading variants with attributes | Batch-fetch `variant_attribute_values` in single JOIN query in `ProductService` |
| Attribute combinations not enforced by DB UNIQUE constraint | Enforced at `ProductService` layer before variant creation |
| ES cannot filter on EAV directly | `SearchIndexService` denormalizes sizes/colors into `availableSizes[]` and `availableColors[]` arrays in `ProductDocument` at index time |
| Admin UI complexity | `AdminProductDetailPage` includes `AttributeManagerPanel` for defining types + values and `CreateVariantDialog` with dynamic attribute inputs |

---

## Consequences

### Positive
- Admins can define new attribute types (e.g. "Material", "Fit") without code changes
- Supports arbitrary combinations of attributes per product type
- `hex_color` enables rich swatch UI without hardcoded color mappings
- ES facets work seamlessly via denormalized arrays

### Negative
- More complex JPA mapping (4 tables instead of 1 for variant attributes)
- `variant_attribute_values` junction table requires careful cascade management
- SKU regeneration not supported — SKU is immutable after creation (by design)

---

## Source References
- `com.ego.raw_ego.catalog.entity.ProductVariant` — variant entity
- `com.ego.raw_ego.catalog.entity.ProductAttributeType` — attribute type entity
- `com.ego.raw_ego.catalog.entity.ProductAttributeValue` — attribute value entity
- `com.ego.raw_ego.catalog.service.ProductService` — variant creation + validation
- `com.ego.raw_ego.search.service.SearchIndexService` — EAV → ES denormalization
- `docs/backend/product-attribute-system.md` — attribute system reference
