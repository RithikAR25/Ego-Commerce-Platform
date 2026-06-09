# BACKEND REQUIREMENTS (Generated from Frontend Audit)

> **Date:** 2026-06-01
> **Rule:** No fake features or frontend-only workarounds allowed for backend-dependent capabilities.

The following backend capabilities are required to unblock enterprise frontend features:

---

## ✅ RESOLVED

### ~~Stock Urgency Indicator~~ — **IMPLEMENTED 2026-06-02**
**Feature Requested:** Show "Only X left in your size" inline on the Product Detail Page (size-specific scarcity messaging).
**Resolution:** Backend now exposes three new fields per variant in `GET /api/v1/products/{slug}`:
- `quantityAvailable` (integer) — raw unit count
- `lowStock` (boolean) — true when qty 0–10
- `stockUrgencyMessage` (string | null) — human-readable message, null when no urgency

**Files changed:**
- `StockUrgencyService.java` *(new)* — centralized urgency rules
- `VariantResponse.java` — 3 new fields + enriched `from()` factory
- `ProductDetailResponse.java` — enriched `from()` overload
- `ProductService.java` — wires `StockUrgencyService` into storefront + admin detail endpoints
- `StockUrgencyServiceTest.java` *(new)* — 5 boundary-condition unit tests (all passing)

**No DB migration required.** `quantity_available` was already stored in `inventory_records`.

---

## OPEN — Pending Implementation

## 1. Bulk Order Management
**Feature Requested:** Admin ability to update the status of multiple orders simultaneously (e.g., marking 50 orders as `SHIPPED` at once).
**Why current backend is insufficient:** Admins currently have to click into each order detail page and fire individual `PUT` requests, which is operationally unscalable.
**Exact API changes needed:**
- **Endpoint:** `PUT /api/v1/admin/orders/bulk-status`
- **DTO:**
  ```json
  {
    "orderIds": [101, 102, 103],
    "status": "SHIPPED",
    "trackingUrl": "Optional fallback for bulk"
  }
  ```
**Priority:** P2

---

## 2. Trending Searches
**Feature Requested:** Provide a list of top 5 trending search terms when the user clicks the search bar (before typing).
**Why current backend is insufficient:** Elasticsearch currently only handles explicit query matching. There is no aggregation of search log data to determine what is currently "trending".
**Exact API changes needed:**
- **Endpoint:** `GET /api/v1/search/trending`
- **Response DTO:** `["oversized tee", "cargo pants", "hoodies"]`
**Priority:** P2

---

## 3. Product Recommendations (Cross-sell / Up-sell)
**Feature Requested:** "Customers also bought" or "Complete the Look" section in the Cart Drawer and Product Detail Page.
**Why current backend is insufficient:** The catalog API returns a single product. The frontend cannot query "related products" accurately without downloading the entire catalog.
**Exact API changes needed:**
- **Endpoint:** `GET /api/v1/products/{productId}/related`
- **Response DTO:** Standard `Page<ProductSearchResponse>`
**Priority:** P1

---

## 4. Guest Cart Migration to Database
**Feature Requested:** Persisting cart state for users who drop off and log in from another device without cookies.
**Why current backend is insufficient:** The Redis cart `merge` only happens if the local device has the `sessionId`. True cross-device cart persistence requires saving cart items to MySQL under the `user_id` when authenticated.
**Exact API changes needed:**
- **Endpoint:** Internal service change to sync Redis cart to MySQL on checkout/logout, and load on login.
**Priority:** P3
