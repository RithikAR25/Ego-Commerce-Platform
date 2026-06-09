# Cloudinary Image Upload System — Complete Testing Guide

> **Module:** Phase 3.5 — Cloudinary Image Upload System
> **Backend:** `http://localhost:8080`
> **Swagger UI:** `http://localhost:8080/docs`
> **Date:** May 23, 2026
> **Status:** ✅ Testing Complete — All happy-path and negative tests passed
> **First verified live upload:** `ego/dev/products/2/gallery/rvtcsfaqa7ie6enolthu`

---

## Section 0 — Credentials & Environment Reference

Everything you need in one place before starting.

### Cloudinary Account (Live Dev Credentials)

| Key | Value |
|---|---|
| **Cloud Name** | `dvoggc3xp` |
| **API Key** | `your-api-key` |
| **API Secret** | `your-api-secret` |
| **Cloudinary Dashboard** | https://console.cloudinary.com |
| **Media Library URL** | https://console.cloudinary.com/console/c-xxxxxxxxx/media_library/folders/ego |
| **Environment Folder** | `ego/dev/` |

### Local Server

| Service | URL / Connection |
|---|---|
| **Spring Boot API** | `http://localhost:8080` |
| **Swagger UI** | `http://localhost:8080/docs` |
| **MySQL** | `localhost:3307` / schema: `rawego` / user: `root` / pass: `secret` |

### JWT Auth (needed for all admin endpoints)

The admin token must be obtained fresh each test session — it expires in **50 minutes** (access-token-expiry-ms is set to `3000000` ms in dev).

**Admin credentials (pre-seed if not done):**
```json
{
  "email": "admin@ego.com",
  "password": "SecureAdmin@123"
}
```

### Test Image Assets

Download or create 3 local test images before starting. They should meet these specs:

| File | Format | Size | Notes |
|---|---|---|---|
| `test-gallery-1.jpg` | JPEG | < 2MB | Any product lifestyle photo |
| `test-variant-black.png` | PNG | < 3MB | Product on white background |
| `test-variant-white.webp` | WebP | < 1MB | Product colorway shot |

For quick test images, use any photo from your filesystem. Even a screenshot saved as `.jpg` works fine for testing the upload pipeline.

**Intentional failure test assets** (for negative testing):
| File | Purpose |
|---|---|
| Any `.pdf` or `.gif` file | Tests MIME type rejection → expect 400 |
| Any image file > 5MB | Tests file size rejection → expect 400 |

---

## Section 1 — Pre-Test Setup

### 1.1 Start the Backend

Make sure the following are running before testing:

```powershell
# Ensure Docker MySQL is up (if using Docker)
docker ps

# Start Spring Boot (from raw-ego directory)
./mvnw spring-boot:run
```

Wait for the console to show: `Started RawEgoApplication in X.XXX seconds`

### 1.2 Verify .env Variables Are Loaded

The application reads Cloudinary credentials from the system environment. Since IntelliJ/VS Code may not auto-load `.env` files, confirm the server booted with credentials by checking the startup logs — there should be NO `Could not resolve placeholder` errors.

> **If you see placeholder errors:** Set environment variables manually in your IDE run configuration or terminal:
> ```powershell
> $env:CLOUDINARY_CLOUD_NAME="dvoggc3xp"
> $env:CLOUDINARY_API_KEY="your-api-key"
> $env:CLOUDINARY_API_SECRET="your-api-secret"
> ./mvnw spring-boot:run
> ```

### 1.3 Verify Database Has Required Data

The image upload endpoints require existing products and variants in the database. If your database is empty, run the full setup from [catalog-api-swagger-testing-guide.md](../backend/catalog-api-swagger-testing-guide.md) first.

**Quick check — run this SQL on `rawego`:**
```sql
SELECT id, name, slug, status FROM products LIMIT 5;
SELECT id, sku, product_id FROM product_variants LIMIT 5;
```

You need at least **one product** (any status) and **one variant** to test all image endpoints.

**Minimum required data (record the IDs you'll use throughout this guide):**

| Item | Your value |
|---|---|
| Product ID | `___` (e.g., `1`) |
| Product Slug | `___` (e.g., `oversized-acid-wash-tee`) |
| Variant ID | `___` (e.g., `1`) |

---

## Section 2 — Authenticate as Admin

Every admin endpoint (`/api/v1/admin/**`) requires a Bearer JWT. This is the first step.

### 2.1 Get Admin Access Token

**In Swagger UI (`http://localhost:8080/docs`):**

1. Find `POST /api/v1/auth/login` under the **Auth** tag.
2. Click **Try it out**.
3. Paste this body:
   ```json
   {
     "email": "admin@ego.com",
     "password": "SecureAdmin@123"
   }
   ```
4. Click **Execute**.
5. Copy the `accessToken` from the response:
   ```json
   {
     "success": true,
     "data": {
       "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZ...",
       "refreshToken": "550e8400-e29b-41d4-a716-...",
       ...
     }
   }
   ```

### 2.2 Authorize Swagger

1. Click the **🔒 Authorize** button at the top-right of the Swagger page.
2. In the `bearerAuth` field, paste your `accessToken` value.
3. Click **Authorize** → **Close**.

All subsequent admin requests will automatically include the `Authorization: Bearer <token>` header.

---

## Section 3 — Test Block A: Product Gallery Images

Gallery images are product-level lifestyle shots NOT tied to a specific color variant.

---

### TEST A-1: Upload a Gallery Image ✅ Happy Path

**Endpoint:** `POST /api/v1/admin/products/{productId}/images`
**Swagger Tag:** Product Images

**Steps:**

1. Find `POST /api/v1/admin/products/{productId}/images` in Swagger.
2. Click **Try it out**.
3. Set `productId` = your product ID (e.g., `1`).
4. In the `file` field, click **Choose File** and select your `test-gallery-1.jpg`.
5. Leave `altText` and `displayOrder` blank — both are optional form fields.
6. Click **Execute**.

**Equivalent curl (no metadata JSON part needed):**
```bash
curl -X 'POST' \
  'http://localhost:8080/api/v1/admin/products/1/images' \
  -H 'Authorization: Bearer <your_token>' \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@test1.jpg;type=image/jpeg'
```

**With optional fields:**
```bash
curl -X 'POST' \
  'http://localhost:8080/api/v1/admin/products/1/images' \
  -H 'Authorization: Bearer <your_token>' \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@test1.jpg;type=image/jpeg' \
  -F 'altText=Front lifestyle shot' \
  -F 'displayOrder=0'
```

**Expected Response (201 Created):**
```json
{
  "success": true,
  "message": "Image uploaded successfully.",
  "data": {
    "id": 1,
    "url": "https://res.cloudinary.com/dvoggc3xp/image/upload/ego/dev/products/1/gallery/xxxxxxx",
    "cloudinaryPublicId": "ego/dev/products/1/gallery/xxxxxxx",
    "altText": "",
    "primary": false,
    "displayOrder": 0,
    "transformations": {
      "thumbnail": "https://res.cloudinary.com/dvoggc3xp/image/upload/w_200,h_250,c_fill,g_auto,q_auto,f_auto/ego/dev/products/1/gallery/xxxxxxx",
      "card":      "https://res.cloudinary.com/dvoggc3xp/image/upload/w_400,h_500,c_fill,g_auto,q_auto,f_auto/ego/dev/products/1/gallery/xxxxxxx",
      "detail":    "https://res.cloudinary.com/dvoggc3xp/image/upload/w_800,h_1000,c_fill,g_auto,q_auto,f_auto/ego/dev/products/1/gallery/xxxxxxx",
      "zoom":      "https://res.cloudinary.com/dvoggc3xp/image/upload/w_1600,h_2000,c_limit,q_auto,f_auto/ego/dev/products/1/gallery/xxxxxxx"
    }
  },
  "timestamp": "..."
}
```

**What to verify:**
- [ ] HTTP status is **201 Created**
- [ ] `cloudinaryPublicId` starts with `ego/dev/products/1/gallery/`
- [ ] All 4 transformation URLs are present and differ only in the transformation string
- [ ] Open the `transformations.card` URL in your browser → image loads correctly at 400×500
- [ ] Open [Cloudinary Media Library](https://console.cloudinary.com) → navigate to `ego > dev > products > 1 > gallery` → your image appears

**Record the image ID:** `id = ___`

---

### TEST A-2: Upload Gallery Image with Metadata ✅ Happy Path

**Steps:** Same as A-1, but this time fill in the `metadata` part field:

```json
{
  "altText": "Oversized Acid-Wash Tee — Front lifestyle shot",
  "displayOrder": 1
}
```

**Expected:** `altText` and `displayOrder` reflected in the response.

---

### TEST A-3: Get Gallery Images (Public — No Auth Required) ✅

**Endpoint:** `GET /api/v1/products/{productId}/images`

1. Find this endpoint in Swagger (under **Product Images** — public).
2. Set `productId` = your product ID.
3. Click **Execute**.

**Expected:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "url": "https://res.cloudinary.com/dvoggc3xp/...",
      "cloudinaryPublicId": "ego/dev/products/1/gallery/...",
      "altText": "",
      "primary": false,
      "displayOrder": 0,
      "transformations": { ... }
    },
    {
      "id": 2,
      ...
      "displayOrder": 1
    }
  ]
}
```

**What to verify:**
- [ ] Returns both images uploaded in A-1 and A-2
- [ ] Images are sorted by `displayOrder` ascending (0 first, then 1)
- [ ] All transformation URLs are present in each image

---

### TEST A-4: Reorder Gallery Images ✅

**Endpoint:** `PUT /api/v1/admin/products/{productId}/images/reorder`

Use the image IDs you collected from A-1 and A-2. Swap their order:

```json
{
  "order": [
    { "imageId": 2, "displayOrder": 0 },
    { "imageId": 1, "displayOrder": 1 }
  ]
}
```

**Expected (200 OK):** Response returns images in new order (image 2 first, image 1 second).

**Verify:** Call `GET /api/v1/products/{productId}/images` again — order should be reversed.

---

### TEST A-5: Delete Gallery Image ✅

**Endpoint:** `DELETE /api/v1/admin/products/{productId}/images/{imageId}`

1. Set `productId` = your product ID.
2. Set `imageId` = ID from test A-1 (image 1).
3. Execute.

**Expected (200 OK):**
```json
{
  "success": true,
  "message": "Image deleted successfully."
}
```

**What to verify:**
- [ ] HTTP status 200
- [ ] `GET /api/v1/products/{productId}/images` no longer returns the deleted image
- [ ] [Cloudinary Media Library](https://console.cloudinary.com) → image is gone from `ego/dev/products/1/gallery/`
- [ ] Check Spring Boot console logs for: `Cloudinary image deleted: publicId=...`

---

## Section 4 — Test Block B: Variant Images

Variant images are colorway-specific (e.g., all Black hoodie photos). The frontend swaps the gallery when a color swatch is clicked.

---

### TEST B-1: Upload Variant Image (Auto-Primary) ✅ Happy Path

**Endpoint:** `POST /api/v1/admin/products/{productId}/variants/{variantId}/images`

1. Set `productId` = your product ID (e.g., `1`).
2. Set `variantId` = your variant ID (e.g., `1`).
3. Select `test-variant-black.png` as the file.
4. Leave `metadata` empty.
5. Execute.

**Expected (201 Created):**
```json
{
  "success": true,
  "message": "Variant image uploaded successfully.",
  "data": {
    "id": 3,
    "url": "https://res.cloudinary.com/dvoggc3xp/image/upload/ego/dev/products/1/variants/1/xxxxxxx",
    "cloudinaryPublicId": "ego/dev/products/1/variants/1/xxxxxxx",
    "altText": "",
    "primary": true,
    "displayOrder": 0,
    "transformations": {
      "thumbnail": "...",
      "card": "...",
      "detail": "...",
      "zoom": "..."
    }
  }
}
```

**Critical check:** `"primary": true` — **the first image uploaded to a variant must ALWAYS be auto-promoted to primary.**

**Record variant image ID:** `id = ___` (e.g., `3`)

---

### TEST B-2: Upload Second Variant Image (Non-Primary) ✅

Upload a second image to the SAME variant (`variantId` = `1`):

1. Select `test-variant-white.webp`.
2. Metadata: `{ "altText": "Black hoodie — back view", "displayOrder": 1 }`.
3. Execute.

**Expected:** `"primary": false` — only one primary allowed per variant.

**Record second variant image ID:** `id = ___` (e.g., `4`)

---

### TEST B-3: Get Variant Images (Public) ✅

**Endpoint:** `GET /api/v1/products/{productId}/variants/{variantId}/images`

**Expected:** Both images returned. First (`id=3`) has `primary: true`, second (`id=4`) has `primary: false`. Sorted by `displayOrder`.

---

### TEST B-4: Set Primary Variant Image ✅

**Endpoint:** `PATCH /api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}/primary`

Promote the second image (`id=4`) to primary:

1. `productId` = `1`, `variantId` = `1`, `imageId` = `4`.
2. Execute (no request body required).

**Expected (200 OK):**
```json
{
  "success": true,
  "message": "Primary image updated.",
  "data": {
    "id": 4,
    "primary": true,
    ...
  }
}
```

**Verify atomicity:**
- Call `GET /api/v1/products/{productId}/variants/{variantId}/images`
- Image `id=4` → `primary: true`
- Image `id=3` → `primary: false`
- **EXACTLY ONE** image should have `primary: true` ← critical invariant

---

### TEST B-5: Delete Primary Variant Image (Auto-Promote Check) ✅

This tests the critical auto-promotion logic when the primary image is deleted.

Current state: `id=4` is primary, `id=3` is non-primary.

Delete the PRIMARY image (`id=4`):

**Endpoint:** `DELETE /api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}`

Set `imageId` = `4`.

**Expected:** 200 OK

**Critical verify:**
- Call `GET /api/v1/products/{productId}/variants/{variantId}/images`
- `id=3` should now have `"primary": true` — **auto-promoted**
- Spring Boot console log should show: `Auto-promoted next image to primary: imageId=3 variantId=1`

---

### TEST B-6: Reorder Variant Images ✅

Upload a third variant image first (so you have 2+ to reorder):

Then:
```json
PUT /api/v1/admin/products/{productId}/variants/{variantId}/images/reorder
{
  "order": [
    { "imageId": <third_image_id>, "displayOrder": 0 },
    { "imageId": 3, "displayOrder": 1 }
  ]
}
```

**Expected:** Response returns images in new display order.

---

## Section 5 — Test Block C: Negative Tests (Validation & Error Cases)

These tests verify the system correctly rejects invalid inputs.

---

### TEST C-1: Upload Invalid File Format ❌ Expect 400

**Endpoint:** `POST /api/v1/admin/products/{productId}/images`

Upload a `.pdf` or `.gif` file (any non-image or unsupported format).

**Expected (400 Bad Request):**
```json
{
  "success": false,
  "message": "Unsupported image format. Allowed formats: JPG, JPEG, PNG, WebP. Received: application/pdf"
}
```

---

### TEST C-2: Upload Empty File ❌ Expect 400

Submit the upload request with an empty/zero-byte file.

**Expected (400 Bad Request):**
```json
{
  "success": false,
  "message": "Image file must not be empty."
}
```

---

### TEST C-3: Upload File Exceeding 5MB ❌ Expect 400

Find an image > 5MB on your system. If you don't have one, create a fake one:

```powershell
# Create a ~6MB dummy file (Windows PowerShell)
$bytes = New-Object byte[] (6 * 1024 * 1024)
[System.IO.File]::WriteAllBytes("C:\temp\bigfile.jpg", $bytes)
```

Upload `bigfile.jpg`.

**Expected (400 Bad Request):**
```json
{
  "success": false,
  "message": "Image file size exceeds the 5 MB limit. Received: 6 MB."
}
```

---

### TEST C-4: Upload to Non-Existent Product ❌ Expect 404

Use a product ID that doesn't exist (e.g., `productId = 99999`).

**Expected (404 Not Found):**
```json
{
  "success": false,
  "message": "Product not found: id=99999"
}
```

---

### TEST C-5: Upload to Non-Existent Variant ❌ Expect 404

Use a variant ID that doesn't exist (e.g., `variantId = 99999`).

**Expected (404 Not Found):**
```json
{
  "success": false,
  "message": "Variant not found: id=99999"
}
```

---

### TEST C-6: Delete Image Belonging to Wrong Product ❌ Expect 404

Use an image ID that belongs to product `1` but try to delete via product `2`'s endpoint.

**Endpoint:** `DELETE /api/v1/admin/products/2/images/1`

**Expected (404 Not Found):**
```json
{
  "success": false,
  "message": "Image id=1 does not belong to product id=2"
}
```

This validates the anti-IDOR ownership check.

---

### TEST C-7: Set Primary on Image Belonging to Wrong Variant ❌ Expect 404

**Endpoint:** `PATCH /api/v1/admin/products/1/variants/99/images/3/primary`

(Image `3` belongs to variant `1`, not variant `99`)

**Expected (404 Not Found):**
```json
{
  "success": false,
  "message": "Image id=3 does not belong to variant id=99"
}
```

---

### TEST C-8: Reorder with Invalid Image ID ❌ Expect 400

```json
PUT /api/v1/admin/products/1/images/reorder
{
  "order": [
    { "imageId": 99999, "displayOrder": 0 }
  ]
}
```

**Expected (400 Bad Request):**
```json
{
  "success": false,
  "message": "Image id=99999 does not belong to product id=1"
}
```

---

### TEST C-9: Admin Endpoints Without Auth ❌ Expect 401/403

Call any admin upload endpoint **without** providing the Bearer token (click **🔓 Logout** in Swagger Authorize first):

**Endpoint:** `POST /api/v1/admin/products/1/images`

**Expected (401 Unauthorized or 403 Forbidden):**
```json
{
  "success": false,
  "message": "Authentication failed."
}
```

---

### TEST C-10: Alt Text Exceeds 255 Characters ❌ Expect 400

```json
{
  "altText": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
}
```

**Expected (400 Bad Request):**
```json
{
  "success": false,
  "message": "Validation failed. Please check the request and try again.",
  "errors": [
    { "field": "altText", "message": "Alt text must not exceed 255 characters" }
  ]
}
```

---

## Section 6 — Database Verification

After running the happy-path tests, confirm the data is correctly persisted in MySQL.

Connect to: `localhost:3307` | Schema: `rawego` | User: `root` | Pass: `secret`

```sql
-- Verify gallery images saved with correct Cloudinary public_ids
SELECT
  id,
  product_id,
  cloudinary_public_id,
  LEFT(url, 80) AS url_preview,
  alt_text,
  display_order,
  created_at
FROM product_images
ORDER BY product_id, display_order;

-- Verify variant images with primary flag
SELECT
  id,
  variant_id,
  cloudinary_public_id,
  LEFT(url, 80) AS url_preview,
  is_primary,
  display_order,
  created_at
FROM variant_images
ORDER BY variant_id, display_order;

-- Verify primary image invariant — each variant should have EXACTLY 1 primary
SELECT
  variant_id,
  COUNT(*) AS primary_count
FROM variant_images
WHERE is_primary = 1
GROUP BY variant_id
HAVING primary_count != 1;
-- Expected result: EMPTY (0 rows means no violations)
```

---

## Section 7 — Cloudinary Dashboard Verification

After running all happy-path tests, verify the assets in your Cloudinary account.

1. Open [https://console.cloudinary.com](https://console.cloudinary.com)
2. Log in with the account for cloud name `dvoggc3xp`
3. Navigate to **Media Library**
4. Browse: `ego` → `dev` → `products` → `{your product ID}` → `gallery`
5. You should see the images you uploaded

**Verify transformation pre-generation:**
1. Click on any uploaded image
2. Go to the **Transformations** or **Derived Images** tab
3. You should see 4 derived/eager variants: `200×250`, `400×500`, `800×1000`, `1600×2000`

---

## Section 8 — CDN URL Verification

Take a `transformations.card` URL from any upload response and open it in the browser.

**Example URL pattern:**
```
https://res.cloudinary.com/dvoggc3xp/image/upload/w_400,h_500,c_fill,g_auto,q_auto,f_auto/ego/dev/products/1/gallery/abc123xyz
```

**What to check:**
- [ ] Image loads without error
- [ ] Image dimensions are approximately 400×500px (check browser DevTools → Network → image response headers or image properties)
- [ ] Response `Content-Type` header is `image/webp` (if your browser supports WebP — confirms `f_auto` is working)

---

## Section 9 — PDP Integration Verification

Verify that images appear correctly in the full product detail response used by the storefront.

**Endpoint:** `GET /api/v1/products/{slug}`

Use your product's slug (e.g., `oversized-acid-wash-tee`).

**Expected response includes:**
```json
{
  "data": {
    "galleryImages": [
      {
        "id": 2,
        "url": "https://res.cloudinary.com/...",
        "cloudinaryPublicId": "ego/dev/products/1/gallery/...",
        "altText": "Oversized Acid-Wash Tee — Front lifestyle shot",
        "primary": false,
        "displayOrder": 0
      }
    ],
    "variants": [
      {
        "id": 1,
        "sku": "EGO-MEN-0001-BLK-M",
        "images": [
          {
            "id": 3,
            "url": "https://res.cloudinary.com/...",
            "cloudinaryPublicId": "ego/dev/products/1/variants/1/...",
            "primary": true,
            "displayOrder": 0
          }
        ]
      }
    ]
  }
}
```

> **Note:** `transformations` is intentionally `null`/omitted in the PDP response to keep the payload lean. Call `GET /api/v1/products/{productId}/images` for full transformation URLs.

---

## Section 10 — Console Log Verification

The Spring Boot console should show structured logs for each operation. Verify these:

### Upload success
```
INFO  c.e.r.catalog.service.CloudinaryService   : Uploading gallery image for product=1 to folder=ego/dev/products/1/gallery
INFO  c.e.r.catalog.service.CloudinaryService   : Cloudinary upload successful: publicId=ego/dev/products/1/gallery/abc123 bytes=124928
INFO  c.e.r.catalog.service.ProductImageService : Product gallery image saved: id=1 productId=1 publicId=ego/dev/products/1/gallery/abc123 displayOrder=0
```

### Delete success
```
INFO  c.e.r.catalog.service.ProductImageService : Product gallery image deleted from DB: id=1 productId=1
INFO  c.e.r.catalog.service.CloudinaryService   : Cloudinary image deleted: publicId=ego/dev/products/1/gallery/abc123
```

### Auto-primary promotion
```
INFO  c.e.r.catalog.service.VariantImageService : Auto-promoted next image to primary: imageId=3 variantId=1
```

### Validation failure (400)
```
WARN  c.e.r.common.exception.GlobalExceptionHandler : Bad request — illegal argument: Unsupported image format. Allowed formats: JPG, JPEG, PNG, WebP. Received: application/pdf
```

---

## Section 11 — Test Checklist Summary

Use this as your go/no-go checklist before marking Phase 3.5 as verified.

### Gallery Images (Product-level)
- [ ] A-1: Upload valid JPG → 201, correct `cloudinaryPublicId`, 4 transformation URLs
- [ ] A-2: Upload with metadata → altText and displayOrder reflected
- [ ] A-3: GET images (public) → returns sorted list with transformations
- [ ] A-4: Reorder → order persisted correctly
- [ ] A-5: Delete → removed from DB + Cloudinary

### Variant Images
- [ ] B-1: Upload first image → `primary: true` auto-set
- [ ] B-2: Upload second image → `primary: false`
- [ ] B-3: GET variant images (public) → correct sort and primary flag
- [ ] B-4: Set primary → atomic transfer, exactly 1 primary remains
- [ ] B-5: Delete primary → next image auto-promoted to primary
- [ ] B-6: Reorder → order persisted correctly

### Negative Tests
- [ ] C-1: Invalid format → 400
- [ ] C-2: Empty file → 400
- [ ] C-3: File > 5MB → 400
- [ ] C-4: Non-existent product → 404
- [ ] C-5: Non-existent variant → 404
- [ ] C-6: Cross-product image delete → 404
- [ ] C-7: Cross-variant primary set → 404
- [ ] C-8: Reorder with foreign image ID → 400
- [ ] C-9: No auth on admin endpoint → 401/403
- [ ] C-10: Alt text > 255 chars → 400 with field error

### Database & CDN
- [ ] `product_images` table has correct `cloudinary_public_id` values
- [ ] `variant_images` table has exactly 1 `is_primary = 1` per variant
- [ ] Cloudinary Media Library shows images in correct folder structure
- [ ] CDN transformation URLs load images in browser

---

## Section 12 — Troubleshooting Common Failures

| Symptom | Cause | Fix |
|---|---|---|
| `Could not resolve placeholder 'CLOUDINARY_CLOUD_NAME'` | Env vars not loaded | Set them in terminal or IDE run config (see §1.2) |
| `401 Unauthorized` on admin endpoints | Token expired (50min TTL) | Re-login via `POST /api/v1/auth/login` and update Swagger Authorize |
| `500 Internal Server Error` on upload | Cloudinary credentials wrong | Verify `.env` values match Cloudinary dashboard |
| `400 Bad Request: Unsupported image format` | File has wrong MIME type | Ensure the file is a real JPG/PNG/WebP, not just renamed |
| `404 Not Found: Product not found` | Product ID doesn't exist in DB | Check product exists; run catalog setup guide first |
| Image uploads but `primary: false` on first upload | Bug — check `VariantImageService.persistVariantImage()` | Confirm `existingImages` check — it should be `true` when list is empty |
| Transformation URLs return 404 | `cloudinary_public_id` corrupted | Check `public_id` field in DB matches what Cloudinary shows |
| Cloudinary delete succeeds but image still in Media Library | Cloudinary CDN cache | Wait 60 seconds and refresh — CDN invalidation takes time |
| `primary: true` on multiple variant images | Service-layer bug | Check `clearPrimaryFlagForVariant()` was called before setting new primary |

---

## Section 13 — Historic Errors & Resolutions

Errors encountered during initial integration testing of Phase 3.5, documented for future reference.

---

### Error 1: `HttpMediaTypeNotSupportedException` — Content-Type 'application/octet-stream' Not Supported

#### Symptom

First attempt to call `POST /api/v1/admin/products/{productId}/images` via Swagger UI or curl returned:

```json
{
  "message": "An unexpected error occurred [HttpMediaTypeNotSupportedException]: Content-Type 'application/octet-stream' is not supported",
  "success": false
}
```

#### Cause

The upload endpoint was originally defined as:

```java
// BROKEN — original controller signature
@RequestPart(value = "metadata", required = false) @Valid ImageUploadRequest request
```

`@RequestPart` for a non-`MultipartFile` parameter requires the multipart boundary part to carry
`Content-Type: application/json` in its own MIME header. Swagger UI and standard curl never set
this header — they send all form parts as `application/octet-stream`. Spring's
`HttpMediaTypeNotSupportedException` is thrown before the method body is reached.

#### Resolution

Replaced the `@RequestPart` JSON metadata object with flat `@RequestParam` fields in both
[ProductImageController.java](file:///c:/Users/rithik.a/Desktop/EGO_E-commerce/raw-ego/src/main/java/com/ego/raw_ego/catalog/controller/ProductImageController.java)
and
[VariantImageController.java](file:///c:/Users/rithik.a/Desktop/EGO_E-commerce/raw-ego/src/main/java/com/ego/raw_ego/catalog/controller/VariantImageController.java):

```java
// FIXED — flat @RequestParam form fields, no content-type negotiation needed
@RequestParam(required = false) String altText,
@RequestParam(required = false) Integer displayOrder
```

The `ImageUploadRequest` object is now built in-method from these individual params.
Swagger UI renders them as simple text fields. curl sends them as standard `-F` form parts.

**Rule locked:** Upload endpoints that accept multipart files must use flat `@RequestParam`
for supplementary metadata — never `@RequestPart` with a JSON DTO.

---

### Error 2: `ClassCastException` — SDK Internals Reject String and List\<String\> for `eager`

This error went through two iterations before the root cause was fully understood.

#### Iteration A — String to List Cast

**Symptom:**
```json
{
  "message": "An unexpected error occurred [ClassCastException]: class java.lang.String cannot be cast to class java.util.List",
  "success": false
}
```

**Cause:** The `eager` parameter was originally passed as a pipe-joined `String`:
```java
// BROKEN — String, not a List
"eager", String.join("|", TRANSFORM_THUMBNAIL, TRANSFORM_CARD, TRANSFORM_DETAIL, TRANSFORM_ZOOM)
```
Inside the Cloudinary SDK, `Util.putEager()` does `(List) params.get("eager")` — a hard cast
from the raw map value. A `String` fails this cast.

**Attempted fix:** Changed to `List.of(String, String, ...)` — which resolved the `String → List`
cast but exposed the next failure.

#### Iteration B — String to Transformation Cast

**Symptom:**
```json
{
  "message": "An unexpected error occurred [ClassCastException]: class java.lang.String cannot be cast to class com.cloudinary.Transformation",
  "success": false
}
```

**Stack trace pinpointed the exact SDK line:**
```
at com.cloudinary.Util.buildEager(Util.java:139) ~[cloudinary-core-2.3.2.jar]
at com.cloudinary.Util.putEager(Util.java:308)
at com.cloudinary.Util.buildUploadParams(Util.java:53)
```

**Cause:** `Util.buildEager()` iterates the `eager` list and casts **each element** to
`com.cloudinary.Transformation` — not to `String`. The SDK uses a raw, untyped `List` internally:

```java
// SDK source — Util.java ~line 135-140
private static String buildEager(List transformations) {
    for (Object element : transformations) {
        Transformation t = (Transformation) element;  // hard cast — no generics safety
        result.add(t.generate());
    }
}
```

A `List<String>` satisfies the `List` cast but fails at the inner `(Transformation)` element cast.

#### Final Resolution

Replaced all `String` transformation values in the `eager` list with proper
`com.cloudinary.Transformation` SDK objects in
[CloudinaryService.java](file:///c:/Users/rithik.a/Desktop/EGO_E-commerce/raw-ego/src/main/java/com/ego/raw_ego/catalog/service/CloudinaryService.java):

```java
// FIXED — List<Transformation>, each element built with the fluent SDK API
"eager", List.of(
    new Transformation().width(200).height(250).crop("fill").gravity("auto").quality("auto").fetchFormat("auto"),
    new Transformation().width(400).height(500).crop("fill").gravity("auto").quality("auto").fetchFormat("auto"),
    new Transformation().width(800).height(1000).crop("fill").gravity("auto").quality("auto").fetchFormat("auto"),
    new Transformation().width(1600).height(2000).crop("limit").quality("auto").fetchFormat("auto")
),
```

The `TRANSFORM_*` String constants are **kept** in the service — they are still used by
`buildTransformationUrls()` to construct CDN delivery URLs in API responses. They are no
longer passed to the SDK upload call.

#### Lesson Learned

The `cloudinary-http5 v2.3.2` SDK (a rewrite of v1.x) accepts `eager` exclusively as
`List<Transformation>`. The old pipe-separated String format documented in many blog posts
and the v1 SDK README does **not** work in v2.x. Always use the SDK's `Transformation`
fluent builder API for any parameter that accepts a transformation in v2.x.

| Parameter | Accepted type in SDK v2.x |
|---|---|
| `eager` | `List<Transformation>` — NOT String, NOT List<String> |
| `allowed_formats` | `List<String>` |
| `folder` | `String` |
| `eager_async` | `boolean` / `Boolean` |

---

### Verified Working Response (May 23 2026)

After all fixes, the upload endpoint returned a **201 Created** with the correct payload:

```json
{
  "data": {
    "altText": "",
    "cloudinaryPublicId": "ego/dev/products/2/gallery/rvtcsfaqa7ie6enolthu",
    "displayOrder": 0,
    "id": 1,
    "primary": false,
    "transformations": {
      "card":      "https://res.cloudinary.com/dyrgw4dxy/image/upload/w_400,h_500,c_fill,g_auto,q_auto,f_auto/ego/dev/products/2/gallery/rvtcsfaqa7ie6enolthu",
      "detail":   "https://res.cloudinary.com/dyrgw4dxy/image/upload/w_800,h_1000,c_fill,g_auto,q_auto,f_auto/ego/dev/products/2/gallery/rvtcsfaqa7ie6enolthu",
      "thumbnail":"https://res.cloudinary.com/dyrgw4dxy/image/upload/w_200,h_250,c_fill,g_auto,q_auto,f_auto/ego/dev/products/2/gallery/rvtcsfaqa7ie6enolthu",
      "zoom":     "https://res.cloudinary.com/dyrgw4dxy/image/upload/w_1600,h_2000,c_limit,q_auto,f_auto/ego/dev/products/2/gallery/rvtcsfaqa7ie6enolthu"
    },
    "url": "https://res.cloudinary.com/dyrgw4dxy/image/upload/v1779531476/ego/dev/products/2/gallery/rvtcsfaqa7ie6enolthu.jpg"
  },
  "message": "Image uploaded successfully.",
  "success": true
}
```

✅ Image is live in Cloudinary folder `ego/dev/products/2/gallery/`
✅ All 4 transformation URLs resolve correctly
✅ DB record persisted with correct `cloudinary_public_id`

