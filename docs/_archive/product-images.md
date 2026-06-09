# Product Images — Backend Architecture

> **Status:** ✅ Implemented & E2E Tested (May 23 2026)
> **Testing guide:** [cloudinary-image-upload-testing-guide.md](../testing/cloudinary-image-upload-testing-guide.md)

## Overview

The EGO image system supports two categories of images, each with distinct lifecycle rules:

| Type | Entity | Table | Tied to |
|---|---|---|---|
| **Gallery images** | `ProductImage` | `product_images` | `Product` — lifestyle/detail shots |
| **Variant images** | `VariantImage` | `variant_images` | `ProductVariant` — colorway-specific |

---

## Image Architecture Decisions

### Only `public_id` Stored in DB

Only the Cloudinary `public_id` (e.g., `ego/dev/products/42/gallery/abc123`) is stored in `cloudinary_public_id`. Transformation URLs are computed at response-time by `CloudinaryService.buildTransformationUrls()`.

**Why:** If delivery URL formats change (e.g., new transformation presets), only one line of code changes — not thousands of database rows. Cache invalidation is trivial.

### Gallery vs. Variant Images

- **Gallery images** (`product_images`): Not tied to a specific color. Lifestyle shots, brand content, material close-ups. Displayed as a fixed gallery background.
- **Variant images** (`variant_images`): Tied to a `ProductVariant`. The frontend swaps the active image set when the user selects a color swatch. Size selection does NOT change images (locked behavior).

### Primary Image Rule (LOCKED)

Each `ProductVariant` must have exactly **one** `VariantImage` with `is_primary = true`.

- The first image uploaded to a variant is auto-promoted to primary.
- Setting a new primary clears the old one in a single atomic transaction.
- If the primary image is deleted, the next image (lowest `display_order`) is auto-promoted.
- The primary image is shown in:
  - Product listing grid cards (for the active/default color)
  - PDP main image slot (when that color is selected)
  - Cart thumbnail

---

## API Contracts

### Gallery Images

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/products/{productId}/images` | Public | List gallery images with transformation URLs |
| `POST` | `/api/v1/admin/products/{productId}/images` | `ROLE_ADMIN` | Upload gallery image (multipart/form-data) |
| `DELETE` | `/api/v1/admin/products/{productId}/images/{imageId}` | `ROLE_ADMIN` | Delete image from DB + Cloudinary |
| `PUT` | `/api/v1/admin/products/{productId}/images/reorder` | `ROLE_ADMIN` | Bulk reorder display positions |

### Variant Images

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/products/{productId}/variants/{variantId}/images` | Public | List variant images with transformation URLs |
| `POST` | `/api/v1/admin/products/{productId}/variants/{variantId}/images` | `ROLE_ADMIN` | Upload variant image (multipart/form-data) |
| `PATCH` | `/api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}/primary` | `ROLE_ADMIN` | Promote image to primary |
| `DELETE` | `/api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}` | `ROLE_ADMIN` | Delete image from DB + Cloudinary |
| `PUT` | `/api/v1/admin/products/{productId}/variants/{variantId}/images/reorder` | `ROLE_ADMIN` | Bulk reorder display positions |

---

## Request Formats

### Upload (multipart/form-data)

The upload endpoints accept the image binary as a `file` form part. Supplementary metadata
is sent as flat form fields — **NOT** as a JSON object part.

> **Important:** Do NOT send a `metadata` JSON part. Doing so causes
> `HttpMediaTypeNotSupportedException` because Spring's `@RequestParam` binding expects
> plain form fields, not `application/json`-typed multipart parts.

```bash
# Minimal — file only (altText defaults to "", displayOrder auto-appended)
curl -X POST 'http://localhost:8080/api/v1/admin/products/{productId}/images' \
  -H 'Authorization: Bearer <token>' \
  -F 'file=@image.jpg;type=image/jpeg'

# With optional metadata as flat form fields
curl -X POST 'http://localhost:8080/api/v1/admin/products/{productId}/images' \
  -H 'Authorization: Bearer <token>' \
  -F 'file=@image.jpg;type=image/jpeg' \
  -F 'altText=Oversized Acid-Wash Tee — Front shot' \
  -F 'displayOrder=0'
```

**Form parts:**
| Part | Type | Required | Description |
|---|---|---|---|
| `file` | Binary (image) | ✅ Yes | JPG, JPEG, PNG, or WebP. Max 5 MB. |
| `altText` | String (form field) | No | Accessibility alt text. Max 255 chars. |
| `displayOrder` | Integer (form field) | No | Sort position. Defaults to end of list. |

### Reorder

```json
PUT /api/v1/admin/products/{productId}/images/reorder
{
  "order": [
    { "imageId": 5, "displayOrder": 0 },
    { "imageId": 3, "displayOrder": 1 },
    { "imageId": 7, "displayOrder": 2 }
  ]
}
```

---

## Response Format

```json
{
  "id": 42,
  "url": "https://res.cloudinary.com/dvoggc3xp/image/upload/ego/dev/products/1/gallery/abc123",
  "cloudinaryPublicId": "ego/dev/products/1/gallery/abc123",
  "altText": "Oversized Acid-Wash Tee — Front shot",
  "primary": false,
  "displayOrder": 0,
  "transformations": {
    "thumbnail": "https://res.cloudinary.com/dvoggc3xp/image/upload/w_200,h_250,c_fill,g_auto,q_auto,f_auto/ego/dev/products/1/gallery/abc123",
    "card":      "https://res.cloudinary.com/dvoggc3xp/image/upload/w_400,h_500,c_fill,g_auto,q_auto,f_auto/ego/dev/products/1/gallery/abc123",
    "detail":    "https://res.cloudinary.com/dvoggc3xp/image/upload/w_800,h_1000,c_fill,g_auto,q_auto,f_auto/ego/dev/products/1/gallery/abc123",
    "zoom":      "https://res.cloudinary.com/dvoggc3xp/image/upload/w_1600,h_2000,c_limit,q_auto,f_auto/ego/dev/products/1/gallery/abc123"
  }
}
```

`transformations` is `null`/omitted when the image is embedded inside a nested product/variant response (PDP, listing) to keep those payloads lean.

---

## Service Layer Design

### Transaction Boundary Map

```
Controller
    │
    ├── CloudinaryService.uploadXxx()    ← NO @Transactional (external HTTP)
    │
    └── ProductImageService.persistXxx() ← @Transactional (DB write only)
```

```
Controller (delete)
    │
    ├── ProductImageService.deleteXxxFromDb()  ← @Transactional
    │
    └── CloudinaryService.deleteImage()        ← NO @Transactional (best-effort)
```

### Exception Handling

| Scenario | Exception | HTTP Status |
|---|---|---|
| File empty / null | `IllegalArgumentException` | 400 |
| Invalid MIME type | `IllegalArgumentException` | 400 |
| File > 5 MB | `IllegalArgumentException` | 400 |
| Product/variant not found | `ResourceNotFoundException` | 404 |
| Image not found | `ResourceNotFoundException` | 404 |
| Image belongs to wrong product/variant | `ResourceNotFoundException` | 404 |
| Cloudinary SDK error | `ImageUploadException` | 500 |

All exceptions are handled by the global `GlobalExceptionHandler.java`.

---

## SDK Integration Notes (cloudinary-http5 v2.3.2)

The following breaking differences from Cloudinary SDK v1.x were discovered during testing:

| Parameter | v1.x accepted | v2.x requires |
|---|---|---|
| `eager` | Pipe-separated `String` | `List<Transformation>` (SDK objects) |
| `allowed_formats` | Comma-separated `String` | `List<String>` |
| `transformation` | Raw `String` | Omit or use `Transformation` object |

See [cloudinary-image-upload-testing-guide.md](../testing/cloudinary-image-upload-testing-guide.md#section-13--historic-errors--resolutions)
for the full error trace and resolution steps.
