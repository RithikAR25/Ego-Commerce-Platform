# E2E Testing Guide — Elasticsearch Search APIs via Swagger UI

> Open Swagger at: **http://localhost:8080/docs**
> All steps can be done in the browser, or via curl/PowerShell.
>
> **Last verified:** 2026-05-29 — all 12 tests pass ✅
> **Architecture reference:** [`docs/backend/search-service.md`](../backend/search-service.md)

---

## Bugs Fixed (run tests after these fixes to verify)

| # | Symptom | Fix | File |
|---|---------|-----|------|
| **Bug 1** | `?minPrice=1200&maxPrice=2000` returned 0 results even when a product's L-variant at 1500 was within range | Price filter now uses overlapping-range logic: `maxPrice >= userMin AND minPrice <= userMax` | `SearchService.java` |
| **Bug 2** | `priceStats.max` showed cheapest price (999) instead of actual upper bound (1500) | Added separate `maxPriceAgg` aggregation on `maxPrice` field | `SearchService.java` |

---

## Step 1 — Open Swagger UI

1. Make sure your Spring Boot backend is running
2. Open your browser and go to:
   ```
   http://localhost:8080/docs
   ```
3. You should see the Swagger UI with all API groups listed
4. Find the **"Search"** tag — click it to expand all 3 search endpoints

---

## Step 2 — Authenticate (for Admin endpoint)

> Skip if you are only testing the public search/autocomplete endpoints.

**2a. Get an Admin JWT** — `POST /api/v1/auth/login`:
```json
{
  "email": "admin@ego.com",
  "password": "Admin@123"
}
```
Copy the `accessToken` from the response.

**2b. Authorize Swagger** — Click 🔒 Authorize → paste token (no "Bearer " prefix) → Authorize → Close.

---

## Step 3 — Seed the Index (Admin Reindex)

> Do this once before running other tests, especially after a fresh ES start.

`POST /api/v1/admin/search/reindex`

**Expected response (200):**
```json
{
  "message": "Reindex complete. N products indexed.",
  "success": true
}
```

> ⚠️ `401` — not authorized (Step 2 skipped).
> ⚠️ `403` — logged in as Customer, not Admin.
> ⚠️ `N=0` — no ACTIVE or OUT_OF_STOCK products in the database yet.

---

## Test A — Browse All Products ✅

**Endpoint:** `GET /api/v1/search` (all fields empty)

**What to verify:**
```json
{
  "data": {
    "content": [ ... ],
    "totalElements": N,
    "fallbackMode": false,
    "facets": {
      "sizes":  [ { "value": "L", "count": 1 }, ... ],
      "colors": [ { "value": "Black", "count": 1 }, ... ],
      "priceStats": {
        "min": 999.0,
        "max": 1500.0,
        "avg": 999.0
      }
    }
  }
}
```

> ❌ `fallbackMode: true` → ES not running. Check Docker: `docker ps | grep elasticsearch`.
> ❌ `priceStats.max` equals `priceStats.min` → Bug 2 not patched yet.

---

## Test B — Full-Text Search ✅

**Endpoint:** `GET /api/v1/search`

| Field | Value |
|-------|-------|
| `query` | `hoodie` |
| `sort` | `createdAt,desc` |
| `page` | `0` |
| `size` | `24` |

**What to verify:**
- `content` contains only products with "hoodie" in name/description/tags
- `fallbackMode: false`
- No-match query (e.g. `query=zzznomatch`) → `totalElements: 0, content: []`

---

## Test C — Size Filter ✅

**Endpoint:** `GET /api/v1/search`

| `sizes` | Expected |
|---------|----------|
| `L` | Products with L variant |
| `S` | Products with S variant |
| `M` | 0 results if no M variant in DB |
| `L,S` | OR logic — products with L **or** S |

**Verify:** Every result in `content` must have the requested size in its `availableSizes` array.

---

## Test D — Color Filter ✅

**Endpoint:** `GET /api/v1/search`

| `colors` | Expected |
|----------|----------|
| `Black` | Products with Black variant |
| `White` | 0 results if no White variant |

**Verify:** Every result's `availableColors` array contains the requested color.

---

## Test E — Price Range Filter ✅ (Bug 1 fixed)

**Endpoint:** `GET /api/v1/search`

| `minPrice` | `maxPrice` | Expected | Reason |
|-----------|-----------|----------|--------|
| `500` | `2000` | Product included | `maxPrice(1500) >= 500` AND `minPrice(999) <= 2000` |
| `1200` | `2000` | Product included ✅ | `maxPrice(1500) >= 1200` — L-variant at 1500 is in range |
| `2000` | `5000` | 0 results | `minPrice(999) <= 5000` but `maxPrice(1500) < 2000` |

**Overlapping-range semantics (post-fix):**
```
Product included if: maxPrice >= userMin AND minPrice <= userMax
```

> **Pre-fix behaviour:** `minPrice=1200` would exclude the product because `999 < 1200`, even though the L-variant at 1500 IS within range.

---

## Test F — Combined Filters ✅

**Endpoint:** `GET /api/v1/search`

| Field | Value |
|-------|-------|
| `query` | `hoodie` |
| `sizes` | `L` |
| `colors` | `Black` |
| `minPrice` | `500` |
| `maxPrice` | `2000` |
| `sort` | `minPrice,asc` |

**What to verify:** Results match all conditions simultaneously. Prices ascending.

---

## Test G — All Sort Orders ✅

Run 4 separate searches with only `sort` changed:

| `sort` | Expected |
|--------|----------|
| `createdAt,desc` *(default)* | Newest products first |
| `createdAt,asc` | Oldest products first |
| `minPrice,asc` | Cheapest first (entry price ascending) |
| `minPrice,desc` | Most expensive first |
| `avgRating,desc` | Highest rated first |

---

## Test H — Autocomplete ✅

**Endpoint:** `GET /api/v1/search/autocomplete?q={q}`

| `q` | Expected |
|-----|----------|
| `h` | `[]` — too short (min 2 chars enforced) |
| `ho` | `["Classic Pullover Hoodie"]` (or similar) |
| `hood` | Narrower suggestions |
| `hoodie` | `["Classic Pullover Hoodie"]` |
| `zzzzz` | `[]` — graceful empty (no error) |

**Example response:**
```json
{
  "success": true,
  "data": ["Classic Pullover Hoodie"]
}
```

---

## Test I — Pagination ✅

**Endpoint:** `GET /api/v1/search`

1. `page=0&size=5` → first 5 products, note their IDs
2. `page=1&size=5` → next 5 — IDs must differ from page 0
3. `page=9999&size=5` → `content: []`, no crash — `totalElements` still correct

```json
{
  "data": {
    "totalElements": N,
    "totalPages": "ceil(N / size)",
    "content": [ ... ]
  }
}
```

---

## Test J — Security ✅

**Endpoint:** `POST /api/v1/admin/search/reindex`

| Test | Credential | Expected |
|------|-----------|----------|
| J1 | No token (logged out) | `401 Unauthorized` |
| J2 | Customer JWT | `403 Forbidden` |
| J3 | Admin JWT | `200 OK` — reindex count returned |

---

## Test K — Category Filter ✅

**Endpoint:** `GET /api/v1/search`

1. Get a valid `categoryId` from `GET /api/v1/categories`
2. Use it: `?categoryId=5` (Hoodies)

**What to verify:**
- Every result's `categoryId` matches the filter value
- `facets.sizes` and `facets.colors` reflect only products in that category

---

## Test L — Edge Cases ✅

| Scenario | Input | Expected |
|----------|-------|----------|
| No match | `query=xyznonexistentproduct123` | `content: [], totalElements: 0` |
| Max page size | `size=100` | Returns up to 100 results |
| Over max | `size=200` | Capped at 100 (no error) |
| Empty query | `query=` | Same as no query — returns all |
| Out of range page | `page=9999` | `content: []`, no crash |

---

## Response Codes Reference

| Code | Meaning |
|------|---------|
| `200 OK` | Success |
| `400 Bad Request` | Invalid param (e.g. negative price) |
| `401 Unauthorized` | No/expired JWT |
| `403 Forbidden` | Wrong role (Customer hitting Admin endpoint) |
| `500 Internal Server Error` | Check Spring Boot logs |

---

## Quick Checklist

```
☑ http://localhost:8080/docs opens
☑ "Search" tag visible with 3 endpoints
☑ Admin JWT obtained + Swagger authorized
☑ POST /admin/search/reindex → "Reindex complete. N products indexed."
☑ GET /search (no filters) → content has products, fallbackMode=false
☑ GET /search?query=hoodie → filtered results
☑ GET /search?sizes=L → all results have L in availableSizes
☑ GET /search?colors=Black → all results have Black in availableColors
☑ GET /search?minPrice=1200&maxPrice=2000 → product with maxPrice=1500 INCLUDED ← Bug 1 fix
☑ priceStats.max = actual max of maxPrice, not minPrice ← Bug 2 fix
☑ GET /search/autocomplete?q=h → [] (too short)
☑ GET /search/autocomplete?q=ho → suggestions
☑ GET /search?page=9999 → content=[], no crash
☑ POST /admin/search/reindex (no token) → 401
☑ POST /admin/search/reindex (customer token) → 403
```

---

## Related Docs

- Architecture & implementation: [`docs/backend/search-service.md`](../backend/search-service.md)
- Catalog module: [`docs/backend/product-module.md`](../backend/product-module.md)
- Category taxonomy: [`docs/backend/category-taxonomy-architecture.md`](../backend/category-taxonomy-architecture.md)
