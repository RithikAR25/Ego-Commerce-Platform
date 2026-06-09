# Product Attribute System — EGO Backend

> **Module:** `com.ego.raw_ego.catalog`
> **Status:** ✅ Implemented (Phase 3 completion — May 23 2026)

---

## Architecture Overview

The EGO catalog uses a **normalized EAV (Entity-Attribute-Value) architecture** for variant management. Variants are not flat objects with hardcoded `color` and `size` columns. Instead, they dynamically link to attribute values through a join table.

```
Product
├── product_attribute_types  (e.g. "Color", "Size")
│     └── product_attribute_values  (e.g. "Black"/"BLK", "Medium"/"M")
└── product_variants
      └── variant_attribute_values  (join table — links variant to attribute values)
```

### Why Normalized?

If you hardcode `color` and `size` columns on the variant, adding a new attribute (e.g. "Fit", "Material") requires a database `ALTER TABLE`. With the normalized EAV approach, you simply add rows — zero schema migrations needed.

---

## Correct Product Lifecycle

Before a variant can be created, the attribute matrix must be fully set up:

| Step | Action | API |
|------|--------|-----|
| 1 | Create Product | `POST /api/v1/admin/products` |
| 2 | Add Attribute Types | `POST /api/v1/admin/products/{productId}/attribute-types` |
| 3 | Add Attribute Values | `POST /api/v1/admin/attribute-types/{typeId}/values` |
| 4 | Create Variants | `POST /api/v1/admin/products/{productId}/variants` |
| 5 | Upload Images | `POST /api/v1/admin/products/{productId}/variants/{variantId}/images` |

---

## REST API Reference

### GET Attribute Types for a Product
```
GET /api/v1/admin/products/{productId}/attribute-types
Authorization: Bearer {adminToken}
```
**Response:** `List<AttributeTypeDetailResponse>`
```json
[
  {
    "id": 1,
    "name": "Color",
    "displayOrder": 0,
    "values": [
      { "id": 1, "value": "Jet Black", "code": "BLK", "displayOrder": 0, "hexColor": "#1A1A1A", "swatchImageUrl": null },
      { "id": 2, "value": "White",     "code": "WHT", "displayOrder": 1, "hexColor": "#FFFFFF", "swatchImageUrl": null }
    ]
  },
  {
    "id": 2,
    "name": "Size",
    "displayOrder": 1,
    "values": [
      { "id": 3, "value": "Small",  "code": "S", "displayOrder": 0, "hexColor": null, "swatchImageUrl": null },
      { "id": 4, "value": "Medium", "code": "M", "displayOrder": 1, "hexColor": null, "swatchImageUrl": null },
      { "id": 5, "value": "Large",  "code": "L", "displayOrder": 2, "hexColor": null, "swatchImageUrl": null }
    ]
  }
]
```

### Create Attribute Type
```
POST /api/v1/admin/products/{productId}/attribute-types
Authorization: Bearer {adminToken}
Content-Type: application/json

{ "name": "Color", "displayOrder": 0 }
```
- Names are **case-insensitive unique** per product (adding "COLOR" when "Color" exists → 409 Conflict)
- Returns the full `AttributeTypeDetailResponse` for the new type

### Add Attribute Value
```
POST /api/v1/admin/attribute-types/{attributeTypeId}/values
Authorization: Bearer {adminToken}
Content-Type: application/json

{
  "value": "Jet Black",
  "code": "BLK",
  "hexColor": "#1A1A1A",
  "displayOrder": 0
}
```
- `code` must be **uppercase letters and digits** only (`^[A-Z0-9]+$`)
- `code` must be **unique within the attribute type** (adding "BLK" twice → 409 Conflict)
- `code` becomes part of the **immutable SKU** — choose carefully, it cannot be changed after variants reference it
- `hexColor` is optional — only applicable for Color attribute values, shows the swatch in the UI

---

## SKU Generation

The backend auto-generates the SKU when a variant is created:

```
EGO-{CATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE_CODE}
```

Example: `EGO-TEE-0001-BLK-M`

Where:
- `TEE` = category code (from the product's subcategory)
- `0001` = product code (auto-sequential)
- `BLK` = `AttributeValue.code` for the selected Color attribute value
- `M` = `AttributeValue.code` for the selected Size attribute value

---

## Validation Rules

| Rule | Error |
|------|-------|
| Attribute type name must be unique per product (case-insensitive) | `409 Conflict` |
| Attribute value code must be unique per attribute type | `409 Conflict` |
| Code must match `^[A-Z0-9]+$` | `400 Bad Request` |
| `colorAttributeValueId` + `sizeAttributeValueId` must belong to the product | `400 Bad Request` |
| `compareAtPrice` must be strictly greater than `price` | `400 Bad Request` |
| SKU must be globally unique | `409 Conflict` |

---

## ⚠️ Attribute Value Naming Conventions (Elasticsearch Compatibility)

> **This is a hard requirement, not a style preference.** Elasticsearch uses **exact keyword term
> matching** on `availableSizes` and `availableColors`. If inconsistent naming is used across
> products, faceted search filters will silently return incorrect (zero) results.

### Size Values — Use Abbreviations Only

When creating `Size` attribute values via `POST /api/v1/admin/attribute-types/{typeId}/values`,
always use the **abbreviation** as the `value` field:

| ✅ `value` to use | ❌ Never use |
|------------------|-------------|
| `XS` | `Extra Small`, `extra small` |
| `S` | `Small`, `small` |
| `M` | `Medium`, `medium` |
| `L` | `Large`, `large` |
| `XL` | `Extra Large`, `XLarge` |
| `XXL` | `Double XL`, `2XL` |
| `XXXL` | `Triple XL`, `3XL` |

The `code` field (used in SKU generation) is also the abbreviation: `S`, `M`, `L`, `XL`, `XXL`.

### Color Values — Use Title Case

Color `value` fields must use title case with the exact display name:
- ✅ `Black`, `White`, `Olive`, `Navy Blue`, `Charcoal Grey`
- ❌ `black`, `BLACK`, `navy blue`

### Background — Bug Fixed May 28 2026

An earlier data entry inconsistency had Product 1 with sizes `S`, `M`, `L` (abbreviations) and
Product 2 with sizes `Small`, `Large` (full words). When filtering `sizes=L`, ES returned zero
results for Product 2 even though it had "Large" in stock. The fix was a SQL migration:

```sql
UPDATE product_attribute_values SET value = 'S'  WHERE value = 'Small';
UPDATE product_attribute_values SET value = 'M'  WHERE value = 'Medium';
UPDATE product_attribute_values SET value = 'L'  WHERE value = 'Large';
UPDATE product_attribute_values SET value = 'XL' WHERE value = 'Extra Large';
```

Followed by `POST /api/v1/admin/search/reindex` to rebuild the ES index.

See [`docs/integrations/elasticsearch.md`](../integrations/elasticsearch.md) for full details.



## Classes Implemented

| Class | Package | Purpose |
|-------|---------|---------|
| `AttributeTypeRepository` | `repository` | JPA interface for `product_attribute_types` |
| `AttributeService` | `service` | Business logic for type/value CRUD |
| `AttributeController` | `controller` | REST endpoints |
| `CreateAttributeTypeRequest` | `dto/request` | Input DTO for type creation |
| `CreateAttributeValueRequest` | `dto/request` | Input DTO for value creation |
| `AttributeTypeDetailResponse` | `dto/response` | Output DTO with full value list |
