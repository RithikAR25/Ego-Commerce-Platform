# Cloudinary Integration — EGO Platform

## Overview

Cloudinary is the CDN-backed media hosting layer for the EGO platform. All product and variant images are uploaded via the backend Cloudinary Java SDK (`cloudinary-http5 v2.3.2`). The frontend never uploads directly to Cloudinary — all uploads are authenticated through the admin REST API.

---

## Architecture Summary

```
Admin Panel
    │
    │ POST multipart/form-data
    ▼
ProductImageController / VariantImageController
    │
    │ (outside @Transactional)
    ▼
CloudinaryService.uploadProductGalleryImage()
    │ Cloudinary SDK HTTP call
    ▼
Cloudinary CDN
    │ returns { public_id, secure_url }
    ▼
ProductImageService.persistProductImage() — @Transactional
    │
    ▼
MySQL (product_images / variant_images)
```

**Critical boundary rule:** The Cloudinary HTTP call is **never** inside a `@Transactional` context. This prevents holding database connections open during potentially slow (1–5s) upload operations. See `ARCHITECTURE_RULES.md §2 Transaction Management`.

---

## Configuration

### Environment Variables

| Variable | Description | Example |
|---|---|---|
| `CLOUDINARY_CLOUD_NAME` | Cloudinary account cloud name | `your-cloud-name` |
| `CLOUDINARY_API_KEY` | Cloudinary API key | `your-api-key` |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret | `your-api-secret` |
| `CLOUDINARY_ENV` | Environment folder prefix (`dev`/`prod`) | `dev` |

Set in `.env` for local development. In Docker Compose, inject via the `environment:` block. In production, inject via CI/CD secrets or a secrets manager.

### application.properties Bindings

```properties
cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME}
cloudinary.api-key=${CLOUDINARY_API_KEY}
cloudinary.api-secret=${CLOUDINARY_API_SECRET}
cloudinary.environment=${CLOUDINARY_ENV:dev}
```

---

## Folder Structure (LOCKED)

| Image Type | Cloudinary Folder Path |
|---|---|
| Product gallery images | `ego/{env}/products/{productId}/gallery/` |
| Variant-specific images | `ego/{env}/products/{productId}/variants/{variantId}/` |

The `{env}` segment (`dev` or `prod`) ensures complete isolation between environments in the same Cloudinary account. **This structure must not change** — Cloudinary public_ids stored in the database would become invalid.

---

## Upload Flow

### Product Gallery Image

```
POST /api/v1/admin/products/{productId}/images
Content-Type: multipart/form-data

file: <image binary>
metadata: { "altText": "...", "displayOrder": 0 }    ← optional
```

1. Controller calls `CloudinaryService.uploadProductGalleryImage(file, productId)`.
2. `CloudinaryService` validates file type and size, then calls Cloudinary SDK.
3. Cloudinary returns `{ public_id, secure_url }`.
4. Controller calls `ProductImageService.persistProductImage(productId, publicId, secureUrl, metadata)`.
5. Service saves `ProductImage` entity to MySQL.
6. Response includes full transformation URLs.

### Variant Image

```
POST /api/v1/admin/products/{productId}/variants/{variantId}/images
Content-Type: multipart/form-data

file: <image binary>
metadata: { "altText": "...", "displayOrder": 0 }    ← optional
```

Same two-step flow. First upload is auto-promoted to `is_primary = true`.

---

## Transformation Strategy

All transformation URLs are constructed in `CloudinaryService.buildTransformationUrls()` using the stored `cloudinary_public_id`. URLs are generated at response-time, not stored in the database.

| Name | Transformation String | Dimensions | Use Case |
|---|---|---|---|
| `thumbnail` | `w_200,h_250,c_fill,g_auto,q_auto,f_auto` | 200×250px | Cart, order summaries |
| `card` | `w_400,h_500,c_fill,g_auto,q_auto,f_auto` | 400×500px | Product listing grid |
| `detail` | `w_800,h_1000,c_fill,g_auto,q_auto,f_auto` | 800×1000px | PDP main image slot |
| `zoom` | `w_1600,h_2000,c_limit,q_auto,f_auto` | up to 1600×2000px | PDP lightbox zoom |

**Key optimizations:**
- `f_auto` — serves WebP/AVIF to supported browsers, JPEG as fallback. Reduces payload 25–40%.
- `q_auto` — Cloudinary's perceptual quality algorithm. Optimal quality/size balance.
- `c_fill,g_auto` — smart crop with subject-detection gravity.
- `c_limit` (zoom) — never upscales. Preserves image quality on zoom.

### Eager Transformations

At upload time, all four sizes are pre-generated asynchronously via the `eager` parameter with `eager_async: true`. This warms the CDN cache so the first request for each size is served instantly.

---

## Image Validation

Enforced in `CloudinaryService.validateFile()` before any SDK call:

| Rule | Value |
|---|---|
| Allowed MIME types | `image/jpeg`, `image/jpg`, `image/png`, `image/webp` |
| Maximum file size | 5 MB (5,242,880 bytes) |
| Empty file | Rejected immediately |

Violations throw `IllegalArgumentException` → mapped to `400 Bad Request` by `GlobalExceptionHandler`.

---

## Deletion Flow

```
DELETE /api/v1/admin/products/{productId}/images/{imageId}
```

1. `ProductImageService.deleteProductImageFromDb()` — validates ownership, deletes DB record (inside `@Transactional`).
2. `CloudinaryService.deleteImage(publicId)` — calls Cloudinary destroy API (outside transaction, best-effort).

If Cloudinary deletion fails, a `WARN` is logged but the operation is considered successful. The DB record is gone, and the orphaned CDN asset is a hygiene issue, not a consistency issue. A future cleanup job can scan Cloudinary for orphans.

---

## Security

- All upload/delete endpoints are under `/api/v1/admin/**` → require `ROLE_ADMIN` JWT.
- Public image GET endpoints are under `/api/v1/products/**` → `permitAll()`.
- Cloudinary credentials are never exposed in any API response.
- The `public_id` is included in image responses — it is a CDN path identifier, not a secret.
- All Cloudinary SDK calls use HTTPS (`secure: true` in `CloudinaryConfig`).

---

## Implementation Files

| File | Package | Purpose |
|---|---|---|
| `CloudinaryConfig.java` | `common.config` | Spring `@Bean` for Cloudinary SDK instance |
| `CloudinaryService.java` | `catalog.service` | Upload, delete, URL transformation logic |
| `ProductImageService.java` | `catalog.service` | Gallery image lifecycle + DB persistence |
| `VariantImageService.java` | `catalog.service` | Variant image lifecycle + primary logic |
| `ProductImageController.java` | `catalog.controller` | Gallery image REST endpoints |
| `VariantImageController.java` | `catalog.controller` | Variant image REST endpoints |
| `ImageResponse.java` | `catalog.dto.response` | Shared image response DTO with Transformations |
| `ImageUploadRequest.java` | `catalog.dto.request` | Upload metadata DTO |
| `ReorderImagesRequest.java` | `catalog.dto.request` | Bulk reorder request DTO |
| `ImageUploadException.java` | `common.exception` | Cloudinary failure exception → 500 |
