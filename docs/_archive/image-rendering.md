# Image Rendering — Frontend Guide

## Overview

The backend returns pre-computed transformation URLs for every image. The frontend must always use these URLs — never construct Cloudinary URLs client-side. This guarantees that URL format changes only require a backend update.

---

## Image Response Structure

Every image API response contains a `transformations` object with four pre-sized CDN URLs:

```typescript
interface ImageTransformations {
  thumbnail: string;  // 200×250px — cart, order summary
  card:      string;  // 400×500px — product grid listing
  detail:    string;  // 800×1000px — PDP main image hero
  zoom:      string;  // 1600×2000px — PDP lightbox zoom
}

interface ImageResponse {
  id:                  number;
  url:                 string;             // original CDN URL (no transform)
  cloudinaryPublicId:  string;             // stable identifier, do not construct URLs from this
  altText:             string;
  primary:             boolean;
  displayOrder:        number;
  transformations?:    ImageTransformations;  // present on upload/image endpoints; null on PDP embed
}
```

> **Note:** `transformations` is `null` when images are embedded inside the full product detail response (`GET /api/v1/products/{slug}`). Use the raw `url` for embedded contexts, or call the dedicated image endpoints to get the full transformation set.

---

## Which URL to Use Where

| Context | URL to use | Dimensions |
|---|---|---|
| Product listing grid card | `transformations.card` | 400×500px |
| PDP main image slot | `transformations.detail` | 800×1000px |
| PDP lightbox zoom overlay | `transformations.zoom` | up to 1600×2000px |
| Cart line item thumbnail | `transformations.thumbnail` | 200×250px |
| Order summary thumbnail | `transformations.thumbnail` | 200×250px |
| og:image meta tag | `url` (original) | original |

---

## Variant Color Swatch Behavior (LOCKED)

When a user selects a **color swatch**, the frontend must:

1. Find the `ProductVariant` with a matching color `AttributeValue`.
2. Fetch `GET /api/v1/products/{productId}/variants/{variantId}/images`.
3. Replace the active image set with the returned images.
4. Display the image where `primary === true` in the main image slot first.
5. Populate the thumbnail strip with all images ordered by `displayOrder`.

When a user selects a **size**, the image gallery does **NOT** change. Size variants share the same colorway images.

---

## Product Detail Page Image Strategy

```
GET /api/v1/products/{slug}
↓
ProductDetailResponse.variants[].images[]  ← VariantImage[], primary + strip
ProductDetailResponse.galleryImages[]      ← ProductImage[], lifestyle shots
```

**Recommended initial render:**
1. Find the first active variant (or default selected variant).
2. Display its `primary === true` variant image as the hero.
3. Show all that variant's images in the thumbnail strip below the hero.
4. Show `galleryImages` in a secondary section below the main gallery.

**On color swatch click:**
1. Replace variant images by fetching the new variant's images.
2. Keep `galleryImages` unchanged — they are product-level, not color-specific.

---

## Lazy Loading

All `<img>` tags using Cloudinary URLs should include `loading="lazy"` except the primary/hero image (above the fold) which should use `loading="eager"` to avoid layout shift (LCP optimization).

```tsx
<img
  src={variant.images.find(img => img.primary)?.transformations?.detail ?? variant.images[0]?.url}
  alt={variant.images.find(img => img.primary)?.altText ?? product.name}
  loading="eager"   // hero — above the fold
/>

{thumbnailImages.map(img => (
  <img
    key={img.id}
    src={img.transformations?.card ?? img.url}
    alt={img.altText}
    loading="lazy"  // thumbnail strip — below the fold
  />
))}
```

---

## TypeScript Types

```typescript
// src/types/image.types.ts

export interface ImageTransformations {
  thumbnail: string;
  card:      string;
  detail:    string;
  zoom:      string;
}

export interface ImageResponse {
  id:                 number;
  url:                string;
  cloudinaryPublicId: string;
  altText:            string;
  primary:            boolean;
  displayOrder:       number;
  transformations?:   ImageTransformations;
}
```

---

## API Endpoint Reference

### Public (storefront)

```typescript
// Fetch product gallery (lifestyle shots, brand images)
GET /api/v1/products/{productId}/images
→ ApiResponse<ImageResponse[]>

// Fetch variant images (colorway-specific)
GET /api/v1/products/{productId}/variants/{variantId}/images
→ ApiResponse<ImageResponse[]>
```

### Admin (requires ROLE_ADMIN JWT)

```typescript
// Upload gallery image
POST /api/v1/admin/products/{productId}/images           (multipart/form-data)

// Upload variant image
POST /api/v1/admin/products/{productId}/variants/{variantId}/images   (multipart/form-data)

// Set primary variant image
PATCH /api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}/primary

// Delete gallery image
DELETE /api/v1/admin/products/{productId}/images/{imageId}

// Delete variant image
DELETE /api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}

// Reorder gallery images
PUT /api/v1/admin/products/{productId}/images/reorder

// Reorder variant images
PUT /api/v1/admin/products/{productId}/variants/{variantId}/images/reorder
```

---

## Format Optimization

All Cloudinary URLs include `f_auto` which instructs Cloudinary to serve:
- **WebP** to Chrome, Edge, Firefox (25–40% smaller than JPEG)
- **AVIF** to Chrome 85+ (40–50% smaller than JPEG, where supported)
- **JPEG** as universal fallback

No additional HTML/CSS work is needed — Cloudinary handles content negotiation via the `Accept` header. Simply use the transformation URLs as-is.
