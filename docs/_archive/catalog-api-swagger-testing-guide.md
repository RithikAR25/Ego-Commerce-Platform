# Catalog API — Swagger Testing Guide

> **Module:** `com.ego.raw_ego.catalog`  
> **Phase:** 3 (Catalog Module Verification)  
> **Status:** ✅ Fully Documented & Tested  

This document outlines the step-by-step procedure to perform end-to-end (E2E) verification of the EGO Catalog API using Swagger UI. It also acts as a post-mortem registry for testing errors encountered and their resolutions.

---

## 1. Setup & Database Reset

Before starting the E2E verification flow, reset the database tables to ensure a clean slate, especially to clear out any conflicting SKU records or manual inserts.

### DB Reset Script
Run the following SQL commands in your MySQL client (connected to port `3307`, schema `rawego`):

```sql
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE variant_attribute_values;
TRUNCATE TABLE inventory_records;
TRUNCATE TABLE product_variants;
TRUNCATE TABLE product_attribute_values;
TRUNCATE TABLE product_attribute_types;
TRUNCATE TABLE products;
TRUNCATE TABLE categories;
SET FOREIGN_KEY_CHECKS = 1;
```

### Backend Dev Server
Start the Spring Boot backend server:
* URL: `http://localhost:8080`
* Swagger UI URL: `http://localhost:8080/docs` or `http://localhost:8080/swagger-ui/index.html` (configured dynamically at `/docs`)

---

## 2. End-to-End Verification Steps

### Step 1: Admin Authentication
Admin-level operations require a JWT Bearer Token.

1. Locate **`POST /api/v1/auth/login`** in Swagger.
2. Execute the request using valid admin credentials (e.g. an admin account pre-seeded or registered).
   ```json
   {
     "email": "admin@ego.com",
     "password": "SecureAdmin@123"
   }
   ```
3. Copy the value of the `accessToken` field from the JSON response:
   ```json
   {
     "success": true,
     "data": {
       "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
     }
   }
   ```
4. Click the **🔒 Authorize** button at the top-right of the Swagger page, paste the token, and click **Authorize**.

---

### Step 2: Create Root Category
Root categories group child subcategories and represent major departments.

1. Locate **`POST /api/v1/admin/categories`** in Swagger.
2. Execute the request with a payload representing a root category (indicated by `parentId = null`):
   ```json
   {
     "name": "Men",
     "code": "MEN",
     "description": "Men's Streetwear Collection",
     "parentId": null,
     "displayOrder": 0
   }
   ```
3. Take note of the returned category `id` (e.g., `1`).

---

### Step 3: Create Subcategory (Depth = 1)
Products cannot belong directly to a root category. They must be mapped to a subcategory (enforced at service layer).

1. Locate **`POST /api/v1/admin/categories`** in Swagger.
2. Execute the request with the subcategory payload, referencing the parent ID obtained in Step 2:
   ```json
   {
     "name": "Oversized Tees",
     "code": "TEE",
     "description": "Premium Heavyweight Oversized Tees",
     "parentId": 1,
     "displayOrder": 0
   }
   ```
3. Take note of the returned subcategory `id` (e.g., `2`).

---

### Step 4: Create Product (DRAFT Status)
New products are initialized in `DRAFT` status and are hidden from the storefront.

1. Locate **`POST /api/v1/admin/products`** in Swagger.
2. Execute the request mapping the product to your subcategory ID:
   ```json
   {
     "name": "Oversized Acid-Wash Tee",
     "categoryId": 2,
     "description": "Heavyweight 240GSM cotton t-shirt with a vintage acid-washed look.",
     "material": "100% Cotton",
     "careInstructions": "Machine wash cold inside out. Hang dry.",
     "tags": ["streetwear", "oversized", "acid-wash", "unisex"]
   }
   ```
3. Take note of the returned product `id` (e.g., `1`) and the generated product `slug` (`oversized-acid-wash-tee`).

---

### Step 5: Seed Attribute Types & Values
Because attribute dimensions (e.g., `Color`, `Size`) and values (`Black`, `Medium`) define the variant matrix, they must exist before variants are constructed. Execute the following SQL insert script to bind attributes to the newly created product (Product ID = `1`):

```sql
-- 1. Insert attribute types for Product 1
INSERT INTO product_attribute_types (id, product_id, name, display_order) VALUES (1, 1, 'Color', 0);
INSERT INTO product_attribute_types (id, product_id, name, display_order) VALUES (2, 1, 'Size', 1);

-- 2. Insert values for Color (Type ID = 1)
INSERT INTO product_attribute_values (id, attribute_type_id, value, code, display_order, hex_color) VALUES (1, 1, 'Black', 'BLK', 0, '#1A1A1A');
INSERT INTO product_attribute_values (id, attribute_type_id, value, code, display_order, hex_color) VALUES (2, 1, 'White', 'WHT', 1, '#F5F5F5');
INSERT INTO product_attribute_values (id, attribute_type_id, value, code, display_order, hex_color) VALUES (3, 1, 'Olive', 'OLV', 2, '#6B7B3A');

-- 3. Insert values for Size (Type ID = 2)
INSERT INTO product_attribute_values (id, attribute_type_id, value, code, display_order) VALUES (4, 2, 'S', 'S', 0);
INSERT INTO product_attribute_values (id, attribute_type_id, value, code, display_order) VALUES (5, 2, 'M', 'M', 1);
INSERT INTO product_attribute_values (id, attribute_type_id, value, code, display_order) VALUES (6, 2, 'L', 'L', 2);
INSERT INTO product_attribute_values (id, attribute_type_id, value, code, display_order) VALUES (7, 2, 'XL', 'XL', 3);
```

---

### Step 6: Create Product Variants (SKU Auto-Generated!)
Variants are created by combining Color and Size values. The system automatically computes the SKU according to:  
`EGO-{ROOT_CATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE_CODE}`

1. Locate **`POST /api/v1/admin/products/{productId}/variants`** in Swagger (with `productId` = `1`).
2. Execute variant requests. Example payload for Black/M:
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
3. Observe the response. The variant should be successfully saved, and the generated SKU should be `EGO-MEN-0001-BLK-M`.

---

### Step 7: Transition Product to ACTIVE (Publish)
A product must be manually published to appear in storefront endpoints.

1. Locate **`PATCH /api/v1/admin/products/{id}/status`** in Swagger (with `id` = `1`).
2. Execute the request:
   ```json
   {
     "status": "ACTIVE"
   }
   ```

---

### Step 8: Upload Images (Cloudinary)
1. **Variant Image**: Map a main showcase image to a specific variant:
   * **`POST /api/v1/admin/variants/{id}/images`** (with multipart file payload & `isPrimary` = `true`).
2. **Product Gallery Image**: Upload general lifestyle shots to the product folder:
   * **`POST /api/v1/admin/products/{id}/images`** (with multipart file payload).

---

### Step 9: Storefront PDP Verification
Simulate storefront requests (Authentication is **not** required for public endpoints).

1. Locate **`GET /api/v1/products/{slug}`** with slug `oversized-acid-wash-tee`.
2. Inspect the response payload:
   * **Price Matrix**: Verify variant fields (`price`, `compareAtPrice`, and `discountPercent` derived dynamically as `28%`).
   * **Attribute Matrix**: Ensure category hierarchies, tags, variants, and attribute options match.

---

### Step 10: Stock Depletion & Restoration Sync (Status Automation)
Verify that stock levels automatically manage the storefront status:

1. **Simulate Out-Of-Stock transition**:
   * Locate **`PUT /api/v1/admin/inventory/{variantId}`** (with `variantId` = `1`).
   * Execute with quantity `0`:
     ```json
     {
       "quantity": 0
     }
     ```
   * Retrieve the product status via `GET /api/v1/admin/products/oversized-acid-wash-tee`. Verify status auto-transitioned from `ACTIVE` to `OUT_OF_STOCK`.
2. **Simulate Restock transition**:
   * Execute `PUT /api/v1/admin/inventory/{variantId}` with quantity `50`.
   * Check the product again. Verify status auto-promoted back to `ACTIVE`.

---

## 3. Historic Errors & Resolutions

During verification testing, the following two critical errors were encountered and resolved:

### Issue 1: Transient Object / Cascade Save Exception during Variant Creation

#### Symptom
When calling `POST /api/v1/admin/products/{id}/variants`, the API failed with:
```json
{
  "message": "An unexpected error occurred [InvalidDataAccessApiUsageException]: org.hibernate.TransientPropertyValueException: Persistent instance of 'com.ego.raw_ego.catalog.entity.ProductVariant' references an unsaved transient instance of 'com.ego.raw_ego.catalog.entity.InventoryRecord' (persist the transient instance before flushing) [com.ego.raw_ego.catalog.entity.ProductVariant.inventoryRecord -> com.ego.raw_ego.catalog.entity.InventoryRecord]",
  "success": false
}
```

#### Cause
In `ProductVariant.java`, Cascade configuration on `inventoryRecord` was incorrectly configured or omitted. During variant creation, `ProductVariantService` attempted to build the `InventoryRecord` but saved the entities out of order or separately:
```java
// WRONG PATTERN:
variant = variantRepository.save(variant); // Flush triggers transient error because Hibernate sees inventoryRecord is unsaved
inventoryRepository.save(inventory);
```
If we attempted to save `inventory` first, it failed because the variant had no ID yet. If we saved the `variant` first, Hibernate's flush cycle complained that the variant was referencing a transient, unsaved `InventoryRecord`.

#### Solution
1. **Entity Fix**: In [ProductVariant.java](file:///c:/Users/rithik.a/Desktop/EGO_E-commerce/raw-ego/src/main/java/com/ego/raw_ego/catalog/entity/ProductVariant.java), restored `CascadeType.ALL` to the `inventoryRecord` field:
   ```java
   @OneToOne(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
   private InventoryRecord inventoryRecord;
   ```
2. **Service Fix**: In [ProductVariantService.java](file:///c:/Users/rithik.a/Desktop/EGO_E-commerce/raw-ego/src/main/java/com/ego/raw_ego/catalog/service/ProductVariantService.java), modified the entity wiring to occur **before** the save operation:
   ```java
   // Build variant first
   ProductVariant variant = ProductVariant.builder()...build();
   
   // Wire the inverse side bidirectionally BEFORE saving
   InventoryRecord inventory = InventoryRecord.builder()
           .variant(variant)
           .quantityAvailable(...)
           .build();
   variant.setInventoryRecord(inventory); 

   // Persist parent — cascade saves the child record automatically in the correct order
   variant = variantRepository.save(variant);
   ```

---

### Issue 2: Invalid Data Access Exception for Swagger Sorting Parameter

#### Symptom
When sending a request to `GET /api/v1/products` via Swagger UI, the API failed with:
```json
{
  "message": "An unexpected error occurred [InvalidDataAccessApiUsageException]: Sort expression '[\"string\"]: ASC' must only contain property references or aliases used in the select clause; If you really want to use something other than that for sorting, please use JpaSort.unsafe(…)",
  "success": false
}
```

#### Cause
Swagger UI generates a default placeholder value of `["string"]` for `List<String>` or array parameters in the UI. Under ordinary circumstances, the user executing the test clicks "Execute" without clearing this placeholder. The backend receives `sort=["string"]` as a query parameter.
Spring Data's Pageable handler tries to convert `"[\"string\"]"` into a sorting order, but Hibernate database compilation throws an `InvalidDataAccessApiUsageException` because the column `["string"]` does not exist on the product entity. This was triggering an internal 500 error page.

#### Solution
1. **User instructions**: Clarified Swagger testing guidelines. The `sort` parameter in Swagger UI must either be **completely cleared** (left empty) or set to valid properties (e.g., `createdAt,desc` or `name,asc`).
2. **Global Exception Handler Fallback**: Added a specific handler in [GlobalExceptionHandler.java](file:///c:/Users/rithik.a/Desktop/EGO_E-commerce/raw-ego/src/main/java/com/ego/raw_ego/common/exception/GlobalExceptionHandler.java) to catch `InvalidDataAccessApiUsageException` and return a standard `400 Bad Request` instead of a 500 Server Error:
   ```java
   @ExceptionHandler(InvalidDataAccessApiUsageException.class)
   public ResponseEntity<ApiResponse<Void>> handleInvalidDataAccess(InvalidDataAccessApiUsageException ex) {
       log.warn("Invalid data access usage: {}", ex.getMessage());
       return ResponseEntity
               .badRequest()
               .body(ApiResponse.error(
                       "Invalid query parameter. Use valid field names for sort (e.g. createdAt, name, id)."));
   }
   ```
