# API Contracts & Integration Guide — EGO Platform

This document outlines the standard API contracts, payloads, response envelopes, pagination models, and integration specifications between the storefront client and the backend server.

---

## 1. Global REST Conventions

* **Protocol:** HTTPS (in production), HTTP (in local development)
* **Port:** `8080` (default)
* **Base Path:** `/api/v1`
* **Content Type:** `application/json` (requests and responses)

### Standard Response Envelope (`ApiResponse<T>`)
Every API response returned from the server conforms to a single, standardized envelope structure:

```json
{
  "success": true,
  "message": "Operation description string",
  "data": { ... },
  "errors": null,
  "timestamp": "2026-05-23T12:00:00.000Z"
}
```

### Validation Error Payload (`ApiError`)
When a request fails input validation (HTTP status `400 Bad Request`), the `errors` array is populated with specific field-level validation errors:

```json
{
  "success": false,
  "message": "Validation failed. Please check the request and try again.",
  "data": null,
  "errors": [
    {
      "field": "price",
      "message": "Price must be greater than 0"
    },
    {
      "field": "colorAttributeValueId",
      "message": "Color attribute value is required"
    }
  ],
  "timestamp": "2026-05-23T12:00:00.000Z"
}
```

### Standard Pagination Envelope
Read queries (e.g., `GET /api/v1/products`) return paginated results wrapped in a standard Spring Data page structure within the `data` envelope:

```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 24,
      "offset": 0,
      "paged": true,
      "unpaged": false
    },
    "totalElements": 42,
    "totalPages": 2,
    "last": false,
    "size": 24,
    "number": 0,
    "numberOfElements": 24,
    "first": true,
    "empty": false
  },
  "timestamp": "2026-05-23T12:00:00.000Z"
}
```

---

## 2. Authentication Endpoints

### Register Account
* **Method & Path:** `POST /api/v1/auth/register`
* **Auth Requirement:** Public (None)
* **Request Payload:**
  ```json
  {
    "firstName": "Rithik",
    "lastName": "A",
    "email": "rithik@ego.com",
    "password": "Secure@123",
    "phone": "+919876543210"
  }
  ```
* **Success Response (201 Created):** Returns an `AuthResponse` containing a fresh `accessToken` (JWT), `refreshToken` (UUID), token metadata, and the user profile object.

### Login
* **Method & Path:** `POST /api/v1/auth/login`
* **Auth Requirement:** Public (None)
* **Request Payload:**
  ```json
  {
    "email": "rithik@ego.com",
    "password": "Secure@123"
  }
  ```
* **Success Response (200 OK):** Same `AuthResponse` as registration.

### Refresh Token (Rotation)
* **Method & Path:** `POST /api/v1/auth/refresh`
* **Auth Requirement:** Public (None) - Excluded from the frontend silent-refresh Axios interceptor to prevent recursion.
* **Request Payload:**
  ```json
  {
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
  }
  ```
* **Success Response (200 OK):** Returns a fresh token pair. The previous refresh token is immediately invalidated.

### Logout
* **Method & Path:** `POST /api/v1/auth/logout`
* **Auth Requirement:** Bearer JWT required in the `Authorization` header.
* **Request Payload:**
  ```json
  {
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
  }
  ```
* **Success Response (200 OK):** Revokes the specified refresh token in the database, leaving other active sessions intact.

### Get Current User
* **Method & Path:** `GET /api/v1/auth/me`
* **Auth Requirement:** Bearer JWT required.
* **Success Response (200 OK):** Returns the user profile object.

---

## 3. Product Catalog Endpoints

### List Storefront Products (Paginated)
* **Method & Path:** `GET /api/v1/products`
* **Auth Requirement:** Public (None)
* **Query Parameters:**
  * `page`: Integer (0-indexed, default `0`)
  * `size`: Integer (default `24`)
* **Success Response (200 OK):** Returns a paginated list of `ACTIVE` and `OUT_OF_STOCK` products.

### Get Product Detail
* **Method & Path:** `GET /api/v1/products/{slug}`
* **Auth Requirement:** Public (None)
* **Success Response (200 OK):** Returns full product details, including the variant matrix (Color × Size) and image gallery.
* **Pricing Note:** `cost_price` is **never** returned in this response.
* **Stock Urgency Note:** Each variant in `data.variants[]` now includes three additional fields for size-specific scarcity messaging:

  | Field | Type | Description |
  |---|---|---|
  | `quantityAvailable` | `integer` | Units currently available for purchase. |
  | `lowStock` | `boolean` | `true` when quantity is 0–10 (any urgency signal active). |
  | `stockUrgencyMessage` | `string \| null` | Human-readable message; `null` when no urgency (11+ units). |

  **Urgency rules:**

  | `quantityAvailable` | `lowStock` | `stockUrgencyMessage` |
  |---|---|---|
  | 0 | `true` | `"Out of stock"` |
  | 1 – 3 | `true` | `"Only X left in your size"` |
  | 4 – 10 | `true` | `"Selling fast"` |
  | 11+ | `false` | `null` |

  **Variant Response Shape (excerpt):**
  ```json
  {
    "id": 12,
    "sku": "EGO-TEE-0001-BLK-M",
    "price": 1299.00,
    "compareAtPrice": 1799.00,
    "discountPercent": 28,
    "active": true,
    "stockStatus": "LOW_STOCK",
    "quantityAvailable": 3,
    "lowStock": true,
    "stockUrgencyMessage": "Only 3 left in your size",
    "weightGrams": 250,
    "attributeValues": [...],
    "images": [...]
  }
  ```

### Create Product
* **Method & Path:** `POST /api/v1/admin/products`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:**
  ```json
  {
    "name": "Oversized Acid-Wash Tee",
    "categoryId": 2,
    "description": "Heavyweight 240GSM cotton t-shirt.",
    "material": "100% Cotton",
    "careInstructions": "Machine wash cold. Hang dry.",
    "tags": ["streetwear", "oversized"]
  }
  ```
* **Success Response (201 Created):** Returns the created product detail object. Initial status defaults to `DRAFT`.

### Create Product Variant
* **Method & Path:** `POST /api/v1/admin/products/{productId}/variants`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:**
  ```json
  {
    "colorAttributeValueId": 1,
    "sizeAttributeValueId": 5,
    "price": 1299.00,
    "compareAtPrice": 1799.00,
    "costPrice": 500.00,
    "initialStock": 100,
    "lowStockThreshold": 5,
    "weightGrams": 250
  }
  ```
* **Success Response (201 Created):** Returns the created variant response. The SKU is auto-generated (e.g., `EGO-MEN-0001-BLK-M`).

### Update Variant Pricing
* **Method & Path:** `PUT /api/v1/admin/variants/{variantId}`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:**
  ```json
  {
    "price": 1399.00,
    "compareAtPrice": 1899.00,
    "costPrice": 550.00,
    "weightGrams": 250
  }
  ```
* **Success Response (200 OK):** Returns the updated variant response.

### Update Product Status
* **Method & Path:** `PATCH /api/v1/admin/products/{productId}/status`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:**
  ```json
  {
    "status": "ACTIVE"
  }
  ```
* **Success Response (200 OK):** Returns the updated product detail object.
* **Transition Guard:** Manual transitions between `ACTIVE` and `OUT_OF_STOCK` are blocked; this status is managed automatically based on variant stock levels.

---

## 4. Administrative Inventory Endpoints

### Set Stock Level
* **Method & Path:** `PUT /api/v1/admin/inventory/{variantId}`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:**
  ```json
  {
    "quantity": 50
  }
  ```
* **Success Response (200 OK):** Updates the inventory level. If the product was previously `OUT_OF_STOCK` and is restocked, it auto-transitions back to `ACTIVE` (or vice versa).

---

## 5. Product Image Endpoints

### Get Product Gallery Images
* **Method & Path:** `GET /api/v1/products/{productId}/images`
* **Auth Requirement:** Public (None)
* **Success Response (200 OK):** Returns ordered list of gallery images with transformation URLs.

### Upload Product Gallery Image
* **Method & Path:** `POST /api/v1/admin/products/{productId}/images`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Content-Type:** `multipart/form-data`
* **Form Parts:**
  * `file` (required): Image binary. Accepted: JPG, JPEG, PNG, WebP. Max: 5 MB.
  * `metadata` (optional): JSON object `{ "altText": "...", "displayOrder": 0 }`
* **Success Response (201 Created):**
  ```json
  {
    "id": 42,
    "url": "https://res.cloudinary.com/.../ego/dev/products/1/gallery/abc123",
    "cloudinaryPublicId": "ego/dev/products/1/gallery/abc123",
    "altText": "Oversized Acid-Wash Tee — Front shot",
    "primary": false,
    "displayOrder": 0,
    "transformations": {
      "thumbnail": "https://res.cloudinary.com/.../w_200,h_250,.../abc123",
      "card":      "https://res.cloudinary.com/.../w_400,h_500,.../abc123",
      "detail":    "https://res.cloudinary.com/.../w_800,h_1000,.../abc123",
      "zoom":      "https://res.cloudinary.com/.../w_1600,h_2000,.../abc123"
    }
  }
  ```

### Delete Product Gallery Image
* **Method & Path:** `DELETE /api/v1/admin/products/{productId}/images/{imageId}`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Success Response (200 OK):** Deletes from DB and removes from Cloudinary CDN.

### Reorder Product Gallery Images
* **Method & Path:** `PUT /api/v1/admin/products/{productId}/images/reorder`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:**
  ```json
  {
    "order": [
      { "imageId": 5, "displayOrder": 0 },
      { "imageId": 3, "displayOrder": 1 },
      { "imageId": 7, "displayOrder": 2 }
    ]
  }
  ```
* **Success Response (200 OK):** Returns images in new display order.

---

## 6. Variant Image Endpoints

### Get Variant Images
* **Method & Path:** `GET /api/v1/products/{productId}/variants/{variantId}/images`
* **Auth Requirement:** Public (None)
* **Success Response (200 OK):** Returns ordered list of variant images. `primary: true` marks the hero image.

### Upload Variant Image
* **Method & Path:** `POST /api/v1/admin/products/{productId}/variants/{variantId}/images`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Content-Type:** `multipart/form-data`
* **Form Parts:** Same as product gallery upload.
* **Success Response (201 Created):** Same `ImageResponse` structure. First image auto-promoted to `primary: true`.

### Set Primary Variant Image
* **Method & Path:** `PATCH /api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}/primary`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:** None (path-identified operation)
* **Success Response (200 OK):** Returns the promoted image with `primary: true`. Previous primary is automatically demoted.

### Delete Variant Image
* **Method & Path:** `DELETE /api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Success Response (200 OK):** If deleted image was primary, next image is auto-promoted.

### Reorder Variant Images
* **Method & Path:** `PUT /api/v1/admin/products/{productId}/variants/{variantId}/images/reorder`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:** Same structure as gallery image reorder.
* **Success Response (200 OK):** Returns images in new display order.

---

## 7. Return & Refund Endpoints

### Initiate Return Request
* **Method & Path:** `POST /api/v1/orders/{orderId}/returns`
* **Auth Requirement:** Bearer JWT (Customer — any authenticated user)
* **Request Payload:**
  ```json
  {
    "reason": "DEFECTIVE",
    "reasonDetail": "Item arrived with a torn seam on the left sleeve."
  }
  ```
  * `reason` (required): One of `DEFECTIVE`, `WRONG_ITEM`, `SIZE_ISSUE`, `NOT_AS_DESCRIBED`, `OTHER`.
  * `reasonDetail` (optional): Free text, max 500 characters.
* **Success Response (201 Created):** Returns `ReturnRequestResponse` with `status: REQUESTED`.
* **Business Guards:** Order must be DELIVERED, within 7-day return window, no active return already exists.

### Get Order Return Status
* **Method & Path:** `GET /api/v1/orders/{orderId}/returns`
* **Auth Requirement:** Bearer JWT (Customer — ownership enforced)
* **Success Response (200 OK):** Returns `ReturnRequestResponse` with current status.
* **Error Response (404 Not Found):** If no return request exists for this order.

### Admin: List All Returns
* **Method & Path:** `GET /api/v1/admin/returns`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Query Parameters:**
  * `status` (optional): Filter by `ReturnStatus` — REQUESTED, APPROVED, REJECTED, REFUND_INITIATED, REFUND_COMPLETED
  * `page` (default 0), `size` (default 20)
* **Success Response (200 OK):** Paginated list of return requests, newest first.

### Admin: Get Specific Return
* **Method & Path:** `GET /api/v1/admin/returns/{returnId}`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Success Response (200 OK):** Returns full `ReturnRequestResponse`.

### Admin: Review Return (Approve or Reject)
* **Method & Path:** `PUT /api/v1/admin/returns/{returnId}/review`
* **Auth Requirement:** Bearer JWT (Requires `ROLE_ADMIN`)
* **Request Payload:**
  ```json
  {
    "approve": true,
    "refundAmount": 1299.00,
    "adminNotes": "Refund approved — please ship item back within 3 days."
  }
  ```
  * `approve` (required): `true` to approve, `false` to reject.
  * `refundAmount` (required when approve=true): Rupee amount, must be > 0 and ≤ order `grandTotal`.
  * `adminNotes` (optional): Visible to customer in status response, max 1000 chars.
* **Approval Success Response (200 OK):** Returns `ReturnRequestResponse` with `status: REFUND_COMPLETED`, `razorpayRefundId` set. Order advances to `REFUNDED`. Inventory restored.
* **Rejection Success Response (200 OK):** Returns `ReturnRequestResponse` with `status: REJECTED`. Order unchanged.
* **Error Responses:**
  * `409 Conflict` if return not in REQUESTED status.
  * `409 Conflict` if `refundAmount` exceeds `order.grandTotal`.
  * `409 Conflict` if order has no `razorpay_payment_id` (cash/manual order).

