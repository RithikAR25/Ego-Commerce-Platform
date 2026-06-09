# Search Service — EGO E-Commerce Backend

> **Module:** `com.ego.raw_ego.search`
> **Phase:** 4 (Elasticsearch Integration)
> **Status:** ✅ Implemented + Bug-fixed (2026-05-29)
> **Depends on:** Phase 3 (Catalog Module), Elasticsearch 9.0.1

E2E verification is documented in [`docs/testing/search-swagger-e2e-guide.md`](../testing/search-swagger-e2e-guide.md).

---

## Package Structure

```
com.ego.raw_ego/
└── search/
    ├── document/
    │   └── ProductDocument.java         ← ES document (flat, denormalised)
    │
    ├── repository/
    │   └── ProductSearchRepository.java ← Spring Data ES repository
    │
    ├── service/
    │   ├── SearchService.java           ← core search + facets + autocomplete
    │   └── SearchIndexService.java      ← MySQL → ES document builder
    │
    ├── dto/
    │   ├── SearchRequest.java           ← parsed query params
    │   └── FacetedSearchResponse.java   ← paged results + facets
    │
    ├── controller/
    │   └── SearchController.java        ← public GET endpoints + admin reindex
    │
    ├── job/
    │   └── ReindexJob.java              ← nightly scheduled full reindex
    │
    └── entity/
        └── (none — ES uses documents, not JPA entities)
```

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/search` | Public | Faceted product search with filters, sort, pagination |
| `GET` | `/api/v1/search/autocomplete` | Public | Prefix suggestions (min 2 chars, max 5 results) |
| `POST` | `/api/v1/admin/search/reindex` | ADMIN | Full reindex of all ACTIVE/OUT_OF_STOCK products |

---

## ProductDocument (ES Index Schema)

```
Index: products
Settings: elasticsearch/product-index-settings.json
Mappings: elasticsearch/product-index-mappings.json
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Product ID — ES document ID |
| `name` | `text` + `autocomplete_analyzer` | Full-text + edge-ngram prefix search |
| `description` | `text` + `search_analyzer` | Full-text search |
| `slug` | `keyword` | URL slug — returned in results, not searched |
| `categoryId` | `Long` | FK for exact category filter |
| `categoryName` | `text` | Boosted in multi-match queries (`^2`) |
| `tags` | `keyword[]` | Exact faceting + boosted search (`^2`) |
| `primaryImageUrl` | `keyword` | Stored only — not indexed |
| `availableSizes` | `keyword[]` | Denormalised from all active variants |
| `availableColors` | `keyword[]` | Denormalised from all active variants |
| `colorHexCodes` | `keyword[]` | Stored only — for frontend swatch rendering |
| `minPrice` | `double` | Lowest variant price — used for price range filter + sort |
| `maxPrice` | `double` | Highest variant price — used for price range filter |
| `totalStock` | `int` | Summed across all active variants — drives in-stock boost |
| `avgRating` | `float` | Pre-computed from reviews — used for rating sort |
| `reviewCount` | `int` | Stored only |
| `isActive` | `boolean` | **Visibility guard** — always filtered `true`; DRAFT/ARCHIVED = false |
| `createdAt` | `date` | Used for "Newest Arrivals" sort |

### Custom Analyzers

| Analyzer | Applied to | Purpose |
|----------|-----------|---------|
| `autocomplete_analyzer` | `name` (index time) | Edge-ngram 2→15 chars for prefix matching |
| `autocomplete_search_analyzer` | `name` (query time) | Standard tokenizer — prevents ngram explosion |
| `search_analyzer` | `description` | Lowercase + ASCII folding |

---

## Search Query Architecture

### Relevance Tuning

```java
// Multi-match with field boosting
multiMatch(query)
  .fields("name^5", "categoryName^2", "tags^2", "description^1")
  .type(BestFields)
  .fuzziness(AUTO)

// In-stock ranking boost (weight 1.5)
functionScore()
  .filter(totalStock > 0)
  .weight(1.5)
  .boostMode(MULTIPLY)
```

### Filter Logic

| Filter | Field | Logic |
|--------|-------|-------|
| `query` | `name`, `categoryName`, `tags`, `description` | Multi-match (fuzzy) |
| `categoryId` | `categoryId` | Exact term filter |
| `sizes` | `availableSizes` | Terms filter (OR within sizes) |
| `colors` | `availableColors` | Terms filter (OR within colors) |
| `minPrice` / `maxPrice` | `maxPrice` + `minPrice` | **Overlapping range** (see below) |
| Visibility | `isActive` | Always `true` — DRAFT/ARCHIVED never returned |

---

## Price Range Filter — Overlapping Range Logic

> **Fixed 2026-05-29** — previous implementation used `minPrice`-only range.

### The Problem with minPrice-only Filtering

**Old implementation:**
```java
range("minPrice").gte(userMin).lte(userMax)
// Condition: userMin ≤ minPrice ≤ userMax
```

**Example failure:** Product with `minPrice=999, maxPrice=1500` and user filter `minPrice=1200, maxPrice=2000`:
- The L-variant costs **1500** — which IS within the 1200–2000 range
- Old filter: `999 >= 1200` → `false` → product excluded ❌

### The Fix — Overlapping Range

A product should appear if **any variant** falls within the requested range.
Using `maxPrice >= userMin AND minPrice <= userMax` correctly identifies all products
whose price range overlaps with the user's budget window.

```java
// NEW: overlapping range — includes products with any variant in range
if (minPrice != null) {
    // Product has at least one variant priced at or above user's floor
    bool.filter(range("maxPrice").gte(minPrice));
}
if (maxPrice != null) {
    // Product has at least one variant priced at or below user's ceiling
    bool.filter(range("minPrice").lte(maxPrice));
}
```

**Visual example:**

```
User filter:   [─────────────  1200 ──────── 2000  ─────────]

Product A:     [999 ──── 1500]              → maxPrice(1500) >= 1200 ✅  minPrice(999) <= 2000 ✅  INCLUDED
Product B:     [─────────────────────── 2500 ──── 4000]  → minPrice(2500) > 2000 ❌  EXCLUDED
Product C:     [200 ─ 500]                  → maxPrice(500) < 1200 ❌  EXCLUDED
Product D:     [1400 ─── 1800]             → INCLUDED ✅
```

---

## Aggregations / Facets

### Active Aggregations (run alongside every search)

| Aggregation Name | Type | Field | Purpose |
|-----------------|------|-------|---------|
| `sizes` | `terms` | `availableSizes` | Size facet counts (top 30) |
| `colors` | `terms` | `availableColors` | Color facet counts (top 20) |
| `priceStats` | `stats` | `minPrice` | `min` and `avg` of cheapest variant price per product |
| `maxPriceAgg` | `max` | `maxPrice` | True upper price bound across all matching products |

### priceStats Response

```json
"priceStats": {
  "min": 999.0,    ← min(minPrice)  — cheapest entry price in results
  "max": 1500.0,   ← max(maxPrice)  — most expensive price in results
  "avg": 999.0     ← avg(minPrice)  — average entry price in results
}
```

> **Why two aggregations?** A single `stats` aggregation on `minPrice` computes `max = max(minPrice)`, not `max(maxPrice)`. For a product with `minPrice=999, maxPrice=1500`, the old single-aggregation approach would return `priceStats.max=999` instead of the correct `1500`.
>
> **Fixed 2026-05-29** by adding a separate `maxPriceAgg` (`max` aggregation on `maxPrice`) and using it in `extractPriceStats()`.

---

## Sort Orders

| `sort` param | ES Sort Field | Direction |
|-------------|--------------|-----------|
| `createdAt,desc` *(default)* | `createdAt` | Desc — newest first |
| `createdAt,asc` | `createdAt` | Asc — oldest first |
| `minPrice,asc` | `minPrice` | Asc — cheapest first |
| `minPrice,desc` | `minPrice` | Desc — most expensive first |
| `avgRating,desc` | `avgRating` | Desc — highest rated first |

---

## Autocomplete

```
GET /api/v1/search/autocomplete?q={q}
```

- Minimum query length: **2 characters** (shorter returns `[]`)
- Maximum results: **5 suggestions**
- Uses `autocomplete_search_analyzer` at query time (standard tokenizer)
- Filtered by `isActive=true` — DRAFT/ARCHIVED products never suggested
- Falls back to empty list on ES failure (no error surfaced to client)

---

## Circuit Breaker / Graceful Degradation

All ES calls are wrapped in `try/catch`. On any failure:

1. Log `WARN` with error message
2. Fall back to MySQL (`ProductRepository.findByStatusIn(...)`)
3. Set `fallbackMode: true` in response
4. Return empty facets (MySQL cannot compute ES-style aggregations)

```java
try {
    return executeEsSearch(req);
} catch (Exception e) {
    log.warn("Elasticsearch unavailable — falling back to MySQL search. Error: {}", e.getMessage());
    return mysqlFallback(req);
}
```

Frontend should treat `fallbackMode: true` as a degraded state — hide facet filters.

---

## Indexing Strategy

### When is a product indexed?

| Event | Action |
|-------|--------|
| Product status → `ACTIVE` | `indexProduct(productId)` |
| Product status → `ARCHIVED` | `deleteFromIndex(productId)` |
| Variant created/updated | `indexProduct(productId)` (rebuilds full document) |
| Inventory restocked → OUT_OF_STOCK → ACTIVE | `indexProduct(productId)` |
| Admin `POST /admin/search/reindex` | `reindexAll()` |
| Nightly scheduled job (`ReindexJob`) | `reindexAll()` |

### Reindex batching

`reindexAll()` pages through products in batches of 100 using `findByStatusInOrderByCreatedAtDesc(ACTIVE, OUT_OF_STOCK)`.
Uses `searchRepository.saveAll()` (ES bulk API) for efficiency.

---

## Protection / Limits

| Protection | Value | Config |
|-----------|-------|--------|
| Max page size | 100 | `ego.search.max-page-size` |
| ES query timeout | 5 seconds | Hardcoded in `executeEsSearch()` |
| Autocomplete min length | 2 chars | Hardcoded in `autocomplete()` |
| Autocomplete max results | 5 | Hardcoded in `autocomplete()` |

---

## Known Bugs Fixed

### Bug 1 — Price Range Filter (Fixed 2026-05-29)

| | Details |
|-|---------|
| **Symptom** | `?minPrice=1200&maxPrice=2000` returned 0 results for a product with `minPrice=999, maxPrice=1500` |
| **Root cause** | Filter used `range("minPrice").gte(userMin).lte(userMax)` — filtered only against the cheapest variant price |
| **Fix** | Changed to overlapping-range: `range("maxPrice").gte(userMin)` + `range("minPrice").lte(userMax)` |
| **File** | `SearchService.java` — `executeEsSearch()` price filter block |

### Bug 2 — priceStats.max Aggregation (Fixed 2026-05-29)

| | Details |
|-|---------|
| **Symptom** | `priceStats.max=999` for a product with `maxPrice=1500` |
| **Root cause** | Single `stats` aggregation ran on `minPrice` — `max = max(minPrice)`, not `max(maxPrice)` |
| **Fix** | Added separate `maxPriceAgg` aggregation (`max` on `maxPrice`). `extractPriceStats()` now uses `maxPriceAgg.value()` for the `max` field |
| **File** | `SearchService.java` — aggregations block + `extractPriceStats()` |

---

## Response Shape — FacetedSearchResponse

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 2,
        "name": "Classic Pullover Hoodie",
        "slug": "classic-pullover-hoodie",
        "minPrice": 999.0,
        "maxPrice": 1500.0,
        "primaryImageUrl": "https://res.cloudinary.com/...",
        "availableSizes": ["S", "L"],
        "availableColors": ["Black"],
        "tags": ["hoodie", "streetwear"],
        "status": "ACTIVE",
        "createdAt": "2026-05-22T14:57:50Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "fallbackMode": false,
    "facets": {
      "sizes": [
        { "value": "L", "count": 1 },
        { "value": "S", "count": 1 }
      ],
      "colors": [
        { "value": "Black", "count": 1 }
      ],
      "priceStats": {
        "min": 999.0,
        "max": 1500.0,
        "avg": 999.0
      }
    }
  }
}
```

---

## Phase Dependencies

| Dependency | Phase |
|-----------|-------|
| Catalog (Product + Category entities) | Phase 3 |
| Elasticsearch 9.0.1 container | Docker Compose |
| Redis (session cache — not used by search) | Docker Compose |
| Nightly reindex cron job | Phase 4 |
| Product status events → auto-index | Phase 4 |
| Admin reindex trigger | Phase 4 |
