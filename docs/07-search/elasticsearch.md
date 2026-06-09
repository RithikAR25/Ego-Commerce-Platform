# Elasticsearch Integration — EGO Platform

> **Module:** `com.ego.raw_ego.search`
> **Status:** ✅ Implemented & E2E Verified (Phase 4/14 — May 28, 2026)
> **ES Version:** `9.0.1` (Docker)
> **Spring Data ES:** `6.0.5` (bundled with Spring Boot 4.0.6)

---

## Overview

Elasticsearch provides faceted product search and autocomplete for the EGO storefront. MySQL is the **system of record**; Elasticsearch is the **read-optimised search index**. They are kept in sync via the Transactional Outbox pattern.

```
MySQL write (Product CRUD)
  → search_outbox row inserted (same transaction)
  → OutboxPoller (every 5s) reads PENDING rows
  → Bulk upsert to Elasticsearch
  → Row marked DONE
```

---

## Frontend Integration

The Elasticsearch APIs are fully wired into the React frontend:

1. **`GET /api/v1/search`** (Faceted Search)
   - **Location:** `src/features/catalog/storefront/pages/ProductListingPage.tsx`
   - **Hook:** `useSearch(params)` (defined in `useSearch.ts`)
   - **Usage:** Powers the main storefront product grid, infinite scrolling, and dynamic left-sidebar facet filters.

2. **`GET /api/v1/search/autocomplete`**
   - **Location:** `src/components/layout/Navbar.tsx`
   - **Hook:** `useAutocomplete(query)`
   - **Usage:** Powers the global search bar in the header. Debounces user input and returns rapid keyword suggestions once 2+ characters are typed.

3. **`POST /api/v1/admin/search/reindex`**
   - **Location:** `src/features/dashboard/admin/pages/AdminDashboardPage.tsx`
   - **Hook:** `useReindex()`
   - **Usage:** Provides a one-click "Reindex Search" button in the admin dashboard header. Shows a loading spinner and toast notification on success/failure to easily resync ES from MySQL.

---

## Docker Setup

```yaml
ego-elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:9.0.1
  container_name: ego-elasticsearch
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false   # dev only — enable in prod
    - ES_JAVA_OPTS=-Xms512m -Xmx512m
  ports:
    - "9200:9200"
  volumes:
    - es_data:/usr/share/elasticsearch/data
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:9200/_cluster/health"]
    interval: 30s
    retries: 5
```

> ⚠️ **Version compatibility:** Spring Boot 4.x bundles the ES Java Client 9.x. This is
> **incompatible with ES 8.x servers** — the client speaks a protocol ES 8 does not understand,
> returning `status 400` with no response body on every request. Always run **ES 9.x** alongside
> Spring Boot 4.x.

---

## Spring Boot Configuration (`application.properties`)

```properties
# Elasticsearch
spring.elasticsearch.uris=${ES_HOST:http://localhost:9200}
spring.elasticsearch.connection-timeout=5s
spring.elasticsearch.socket-timeout=10s

# Search Outbox Poller
ego.search.outbox.poll-interval-ms=5000
ego.search.outbox.batch-size=100
ego.search.max-page-size=100
```

---

## Index Mapping (`ProductDocument.java`)

The `products` index is managed by `@Document(indexName = "products", createIndex = true)`.
Spring Data ES creates the index on first boot if it does not exist.

| Field | ES Type | Purpose |
|-------|---------|---------|
| `id` | `long` | MySQL primary key |
| `name` | `text` (english analyzer) + `keyword` sub-field | Full-text search + autocomplete |
| `description` | `text` (english analyzer) | Full-text search |
| `slug` | `keyword` | Exact match / URL routing |
| `categoryId` | `long` | Category filter |
| `categoryName` | `keyword` | Facet / display |
| `tags` | `keyword[]` | Boosted full-text field |
| `primaryImageUrl` | `keyword` (not indexed) | Display only |
| `availableSizes` | `keyword[]` | Size facet + filter |
| `availableColors` | `keyword[]` | Color facet + filter |
| `colorHexCodes` | `keyword[]` | Frontend swatch display |
| `minPrice` / `maxPrice` | `double` | Price range filter + stats aggregation |
| `totalStock` | `integer` | In-stock ranking boost |
| `avgRating` | `float` | Sort by rating |
| `reviewCount` | `integer` | Display |
| `isActive` | `boolean` | Visibility guard (always filtered) |
| `createdAt` | `date` | Default sort field |

### ⚠️ Critical: `availableSizes` Values Must Be Abbreviations

The `availableSizes` field uses **exact keyword term matching**. The filter query does
`terms: { availableSizes: ["L"] }` — this is case-sensitive and exact. Therefore all size
attribute values stored in `product_attribute_values` **MUST** use the standard abbreviation
format:

| ✅ Correct | ❌ Wrong |
|-----------|---------|
| `S` | `Small` |
| `M` | `Medium` |
| `L` | `Large` |
| `XL` | `Extra Large` |
| `XXL` | `Double XL` |

**Colors** use title case (e.g. `Black`, `White`, `Olive`) — consistent across all products.

> See [Size Data Normalization Bug — May 28 2026](#size-data-normalization-bug--may-28-2026) below.

---

## Sync Architecture — Transactional Outbox Pattern

### Why Not `@Async` Event Listeners?

The original design used `Spring ApplicationEvent` + `@Async` listener to push products to ES.
This is **not durable**: if the app crashes, is redeployed, or the thread pool is saturated,
the indexing event is silently lost.

### Current Architecture

```
ProductService.create/update/archive()
  → (same @Transactional boundary)
  → SearchOutboxEntry saved to search_outbox table
  → Hibernate commit — outbox row is durable in MySQL

OutboxPoller (@Scheduled every 5s)
  → SELECT * FROM search_outbox WHERE status = 'PENDING' LIMIT 100
  → buildDocuments() for each product ID
  → searchRepository.saveAll() — bulk upsert to ES
  → UPDATE search_outbox SET status = 'DONE'
```

### `search_outbox` Table Schema

```sql
CREATE TABLE search_outbox (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  event_type ENUM('UPSERT', 'DELETE') NOT NULL,
  status     ENUM('PENDING', 'DONE', 'FAILED') NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at DATETIME(6),
  INDEX idx_search_outbox_status_created (status, created_at)
);
```

### Classes

| Class | Purpose |
|-------|---------|
| `SearchOutboxEntry` | JPA entity for `search_outbox` |
| `SearchOutboxRepository` | `findTop100ByStatusOrderByCreatedAtAsc(PENDING)` |
| `SearchIndexService` | Builds `ProductDocument` from MySQL data |
| `SearchOutboxPoller` | `@Scheduled` — reads outbox, calls `SearchService.bulkIndex()` |
| `SearchReindexJob` | Admin-triggered full reindex |

---

## Search Query Architecture (`SearchService.java`)

### Relevance Tuning

```
multi-match: name^5, categoryName^2, tags^2, description^1
function_score boost: weight=1.5 for products with totalStock > 0
```

### Filter Logic

All filters are applied as `bool.filter` clauses (zero relevance impact, cached):

| Filter | ES clause |
|--------|-----------|
| Visibility | `term: { isActive: true }` — always applied |
| Full-text | `multi_match` on name, categoryName, tags, description |
| Category | `term: { categorySlugPath: "men" }` — matches ALL products under category slug at any depth (⚠️ Updated: was `categoryId` pre-3-level migration — ADR-001) |
| Sizes | `terms: { availableSizes: ["M", "L"] }` — OR within sizes |
| Colors | `terms: { availableColors: ["Black"] }` — OR within colors |
| Price | `range: { minPrice: { gte: N, lte: M } }` |

### Sort Options

| `sort` param | ES sort |
|-------------|---------|
| `createdAt,desc` | `createdAt DESC` (default) |
| `minPrice,asc` | `minPrice ASC` |
| `minPrice,desc` | `minPrice DESC` |
| `avgRating,desc` | `avgRating DESC` |

### Aggregations (Facets)

```
"sizes"      → terms on availableSizes  (top 30)
"colors"     → terms on availableColors (top 20)
"priceStats" → stats on minPrice        (min/max/avg)
```

---

## Circuit Breaker / Graceful Degradation

All ES calls are wrapped in `try/catch`. On any ES failure:

1. Logs `WARN: Elasticsearch unavailable — falling back to MySQL search`
2. Falls back to `ProductRepository.findByStatusIn()` (MySQL)
3. Sets `fallbackMode: true` in the response
4. Returns empty facets

Autocomplete returns `[]` (empty list) on ES failure — no crash.

The frontend (`ProductListingPage`) shows a yellow alert banner when `fallbackMode: true`.

---

## REST API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/search` | Public | Faceted product search |
| `GET` | `/api/v1/search/autocomplete` | Public | Name prefix suggestions (min 2 chars, max 5 results) |
| `POST` | `/api/v1/admin/search/reindex` | ROLE_ADMIN | Full manual reindex from MySQL |

### Security Config

`/api/v1/search` and `/api/v1/search/autocomplete` are listed in `SecurityConfig.PUBLIC_MATCHERS`.
`/api/v1/admin/search/**` is protected by the `hasRole("ADMIN")` rule.

> ⚠️ **Bug fixed (May 28 2026):** The initial implementation omitted `/api/v1/search` and
> `/api/v1/search/autocomplete` from `PUBLIC_MATCHERS`. This caused `401 Unauthorized` for all
> unauthenticated visitors. Fixed by adding both paths to the array.

---

## Nightly Reindex Job

```java
@Scheduled(cron = "0 0 3 * * *")   // 3 AM daily
public void scheduledFullReindex() { ... }
```

Pages through all `ACTIVE` / `OUT_OF_STOCK` products in batches of 100 and bulk-upserts to ES.
This is the safety net for any sync drift (e.g. failed outbox rows, ES downtime).

---

## Known Bugs Fixed

### Size Data Normalization Bug — May 28 2026

**Symptom:** `GET /api/v1/search?sizes=L` returned 0 results even though products with size L
existed in the database.

**Root cause:** `product_attribute_values` contained inconsistent size naming across products:
- Product 1 (Acid-Wash Tee): sizes stored as `S`, `M`, `L`, `XL` (abbreviations)
- Product 2 (Pullover Hoodie): sizes stored as `Small`, `Large` (full words)

ES does an **exact keyword term match** on `availableSizes`. The query filter `sizes=L` sent
`terms: { availableSizes: ["L"] }` which matched `"L"` but never matched `"Large"`.
The two naming conventions were from separate test data entries and no validation existed to
enforce consistency.

**Fix applied (May 28 2026):**
```sql
UPDATE product_attribute_values SET value = 'S'  WHERE value = 'Small';
UPDATE product_attribute_values SET value = 'M'  WHERE value = 'Medium';
UPDATE product_attribute_values SET value = 'L'  WHERE value = 'Large';
UPDATE product_attribute_values SET value = 'XL' WHERE value = 'Extra Large';
-- followed by full ES reindex via POST /api/v1/admin/search/reindex
```

**Prevention:** The `product-attribute-system.md` doc now explicitly specifies abbreviations
as the canonical format. Admins creating size attributes via the UI should use `S`, `M`, `L`,
`XL`, `XXL`.

### Security Config Bug — May 28 2026

**Symptom:** `GET /api/v1/search` returned `401 Authentication required` for all users,
including logged-in admins.

**Root cause:** `/api/v1/search` and `/api/v1/search/autocomplete` were missing from
`SecurityConfig.PUBLIC_MATCHERS`. Spring Security's `anyRequest().authenticated()` catch-all
blocked them.

**Fix:** Added both paths to `PUBLIC_MATCHERS` in `SecurityConfig.java`.

---

## Operations Runbook

### After Fresh ES Container Start

```powershell
# 1. Confirm ES is healthy
Invoke-RestMethod "http://localhost:9200/_cluster/health" | Select status

# 2. Trigger full reindex (login as admin first)
$token = (Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" `
  -Method Post -ContentType "application/json" `
  -Body '{"email":"admin@ego.com","password":"Admin@123"}').data.accessToken

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/search/reindex" `
  -Method Post -Headers @{ Authorization = "Bearer $token" }
# Expected: "Reindex complete. N products indexed."
```

### Verify Index Contents

```powershell
# Count documents
Invoke-RestMethod "http://localhost:9200/products/_count"

# Inspect a document
Invoke-RestMethod "http://localhost:9200/products/_search?size=1&pretty" | ConvertTo-Json -Depth 10

# Check availableSizes values (should be abbreviations only)
Invoke-RestMethod "http://localhost:9200/products/_search" `
  -Body '{"aggs":{"sizes":{"terms":{"field":"availableSizes","size":30}}},"size":0}' `
  -ContentType "application/json" -Method Post | ConvertTo-Json -Depth 5
```

### Verify Filters Work

```powershell
# Size filter
(Invoke-RestMethod "http://localhost:8080/api/v1/search?sizes=L").data.totalElements

# Color filter
(Invoke-RestMethod "http://localhost:8080/api/v1/search?colors=Black").data.totalElements

# Combined
(Invoke-RestMethod "http://localhost:8080/api/v1/search?sizes=L&colors=Black").data.totalElements
```

---

## Testing Guide

See [`docs/_archive/search-swagger-e2e-guide.md`](../_archive/search-swagger-e2e-guide.md)
for the original Swagger UI E2E testing guide (archived — superseded by
[`docs/E2E_TEST_PLAN.md`](../E2E_TEST_PLAN.md) §16 Search section).

---

> **⚠️ Contradiction Fixed (June 6, 2026):** Category filter was `term: { categoryId: N }` in this
> document. Post-3-level hierarchy migration (ADR-001), `ProductDocument` now uses
> `categorySlugPath: List<String>` for hierarchy-aware category filtering. The filter clause
> has been updated above. See [ADR-001](../13-decisions/architecture-decision-records/ADR-001-category-architecture.md)
> for migration rationale.
