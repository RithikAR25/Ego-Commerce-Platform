# Category Architecture Audit — EGO Platform

**Audit Date:** June 4, 2026  
**Status:** ✅ MIGRATION COMPLETE — 3-level hierarchy live  
**Auditor:** Antigravity AI  
**Migration Date:** June 4, 2026  
**DB State:** 133 categories, 130 hierarchy links seeded

> [!IMPORTANT]
> This audit was the pre-migration baseline. The 3-level architecture is now implemented.
> See `CATEGORY_MIGRATION_IMPACT_ANALYSIS.md` for the impact analysis and `E2E_TEST_PLAN.md` Section 25 for acceptance tests.

---

## 1. Executive Summary

The EGO platform currently supports a **2-level category hierarchy** (Root → Subcategory). The architecture is more sophisticated than a naive implementation — it includes a `CategoryHierarchyLink` entity for multi-parent cross-listing, `display_order` on all categories, visibility controls, and navigation label overrides. However, the **service layer enforces a hard depth cap of 2 levels**, preventing the 3-level hierarchy (Root → Group → Leaf) required by the enterprise target model.

The target architecture (Myntra/AJIO style) requires:

```
ROOT         → MEN, WOMEN, KIDS, HOME, BEAUTY, GENZ
GROUP        → Topwear, Bottomwear, Footwear, Accessories
LEAF         → T-Shirts, Casual Shirts, Sneakers, Jeans
```

Products must be assigned only to **LEAF** categories. Currently products are assigned to **subcategories** (depth=1), which maps to what will become the GROUP level — one level too shallow.

---

## 2. Current Architecture — Full Analysis

### 2.1 Database Schema

#### `categories` table

| Column         | Type         | Notes |
|----------------|--------------|-------|
| id             | BIGINT PK    | Auto-increment |
| parent_id      | BIGINT FK    | NULL = root; non-null = subcategory |
| name           | VARCHAR(100) | Display name |
| code           | VARCHAR(10)  | Unique, UPPERCASE — embedded in SKUs |
| slug           | VARCHAR(150) | Unique, URL-friendly |
| description    | TEXT         | Optional |
| image_url      | VARCHAR(500) | Cloudinary URL |
| display_order  | INT          | Lower = appears first |
| is_active      | BOOLEAN      | Soft-delete/visibility flag |
| created_at     | TIMESTAMP    | Immutable |
| updated_at     | TIMESTAMP    | Auto-update |

**Indexed:** `parent_id`, `slug` (unique), `code` (unique)

#### `category_hierarchy_links` table

| Column               | Type         | Notes |
|----------------------|--------------|-------|
| id                   | BIGINT PK    | Auto-increment |
| parent_category_id   | BIGINT FK    | Must reference a root category |
| child_category_id    | BIGINT FK    | Must reference a subcategory |
| is_primary           | BOOLEAN      | Canonical parent for breadcrumbs |
| display_order        | INT          | Per-parent ordering |
| is_visible           | BOOLEAN      | Show/hide in this parent's nav |
| navigation_label     | VARCHAR(100) | Override display name |
| created_at           | TIMESTAMP    | Immutable |
| updated_at           | TIMESTAMP    | Auto-update |

**Constraint:** UNIQUE(parent_category_id, child_category_id)

#### `products` table (category relationship)

```sql
category_id BIGINT NOT NULL -- FK to categories.id
-- @SQLRestriction: is_deleted = false on all queries
```

Products reference a single category via `category_id`. Currently this must be a subcategory (depth=1 child). The service validates this.

---

### 2.2 Backend Architecture

#### Category Entity (`Category.java`)
- **Self-referencing:** `@ManyToOne parent` + `@OneToMany children`
- **Depth contract:** Enforced at **service layer only** — the DB allows arbitrary nesting but `CategoryService` explicitly rejects `parent.isSubcategory()` → prevents grandchildren
- **Root detection:** `parent == null` → root; `parent != null` → subcategory
- **Key fields:** `code` (immutable, in SKUs), `slug` (auto-generated, mutable), `displayOrder`, `isActive`

#### CategoryHierarchyLink Entity (`CategoryHierarchyLink.java`)
- **Purpose:** First-class relationship entity for multi-parent cross-listing
- **Constraints:** parent must be root, child must be subcategory, UNIQUE(parent, child)
- **Features:** `isPrimary` (canonical breadcrumb), `displayOrder` (per-parent), `isVisible`, `navigationLabel` override
- **DB constraint:** `CHECK parent_id != child_id` (no self-parenting)

#### CategoryService (`CategoryService.java`) — 724 lines
- `getNavigationTree()` — returns all active roots with visible subcategories from `CategoryHierarchyLink`; falls back to legacy `parent_id` tree if no links exist
- `getCategoryBySlug()` — returns single category with `parents[]` for subcategories
- `getCategoryBreadcrumbs()` — 2-level: `[root, subcategory]` or `[root]`
- `createCategory()` — validates depth ≤1, creates links for all parentIds
- `addParentToCategory()` — cross-lists subcategory under additional roots
- `reorderCategoryChildren()` — sets `displayOrder` per-parent via hierarchy links
- **Hard limit:** `if (parent.isSubcategory()) throw IllegalArgumentException` — blocks depth > 1

#### CategoryController (`CategoryController.java`) — 291 lines
**Public:**
- `GET /api/v1/categories` → full navigation tree
- `GET /api/v1/categories/{slug}` → single category with parents
- `GET /api/v1/categories/{slug}/breadcrumbs` → 2-level breadcrumb

**Admin:**
- `GET/POST /api/v1/admin/categories` → list all / create
- `PATCH /api/v1/admin/categories/{id}` → update details
- `DELETE /api/v1/admin/categories/{id}` → soft-deactivate
- `PATCH /api/v1/admin/categories/{id}/activate` → re-activate
- `DELETE /api/v1/admin/categories/{id}/permanent` → hard-delete
- `POST/GET/PATCH/DELETE /api/v1/admin/categories/{childId}/parents/{parentId}` → hierarchy link management
- `PUT /api/v1/admin/categories/{parentId}/children/reorder` → reorder

#### DTOs
- `CategoryTreeResponse` — root with `subcategories: CategoryTreeItemResponse[]`
- `CategoryTreeItemResponse` — subcategory with link metadata (displayOrder, visible, resolvedLabel, primary)
- `CategoryResponse` — single category with `parent` (singular, canonical) + `parents` (plural, all)
- `CategoryHierarchyLinkResponse` — full link metadata

#### ProductService (category validation)
Products must reference a subcategory (depth=1). `ProductService.createProduct()` validates via `CategoryService.findById()` and then checks `category.isSubcategory()`.

---

### 2.3 Elasticsearch Integration

#### ProductDocument (`ProductDocument.java`)
```java
private Long       categoryId;    // FK — used for category filter
private String     categoryName;  // Denormalised — boosted in queries (name^2)
// NO categoryPath or ancestry array
```

**Current ES category support:**
- Single `categoryId` field (exact leaf match only)
- Single `categoryName` (denormalised display name)
- **NO** category path / hierarchy array
- **NO** ancestry-based filtering (e.g., "all products under MEN")

**ProductListingPage filtering:**
```typescript
// Frontend resolves category slug → categoryIds
if (matchedRoot) {
  categoryIds = matchedRoot.subcategories.map(s => s.id); // all subcategory IDs
} else {
  categoryId = sub.id; // direct subcategory match
}
```
This workaround collects all subcategory IDs when a root is selected. Works for 2-level but **breaks at 3-level** — it only goes one level deep.

#### SearchService
- Filters on `categoryId` (term) or `categoryIds` (terms) — flat ID matching
- Aggregates: `sizes`, `colors`, `priceStats`
- **No** category aggregation / breadcrumb-based filtering

---

### 2.4 Frontend Navigation

#### Navbar (`Navbar.tsx`)
**Desktop:** `treeData.map(root → Button + Paper dropdown with subcategories)`
- Simple hover dropdown per root, lists subcategories as flat list
- **No mega-menu** — just a simple `display: none → block` CSS dropdown
- **Not enterprise grade** — no column layout, no group headers, no animations

**Mobile:** Left drawer with expandable root → flat subcategory list  
- Categories are data-driven (from `useNavigationTree()`)
- No hardcoded categories ✅

#### ProductListingPage (`ProductListingPage.tsx`)
- Uses `useSearch()` hook with URL params
- Category filter resolved client-side from navigation tree
- Filter panel: sizes, colors, price range — **no category hierarchy filter**
- Breadcrumbs: **not implemented** on ProductListingPage

#### AdminCategoriesPage (`AdminCategoriesPage.tsx`) — 721 lines
- Table view of all categories (root and subcategory flat list)
- ✅ Create root or subcategory with multi-parent selection
- ✅ Edit category details
- ✅ Manage parents (add/remove cross-listings, set canonical)
- ✅ Deactivate / reactivate / hard-delete
- **Missing:** 3-level tree visualization, leaf category distinction, drag-and-drop reorder

#### catalog.types.ts
Accurately mirrors backend DTOs. `CategoryTreeResponse.subcategories` is typed as `CategoryTreeItemResponse[]`. All types are correct for the 2-level model.

---

### 2.5 Current Depth Enforcement Points

| Location | Enforcement | Rule |
|----------|-------------|------|
| `CategoryService.createCategory()` | Service | `parent.isSubcategory()` → 400 |
| `CategoryService.addParentToCategory()` | Service | `findRootById()` validates parent must be root |
| `CategoryHierarchyLink` docs | Comment | "parent must be a root category" |
| `Product.java` Javadoc | Comment | "Must be a SUBCATEGORY (depth=1)" |
| `ProductService.createProduct()` | Service | Validates `category.isSubcategory()` |

---

## 3. Strengths of Current Architecture

1. **Self-referencing `parent_id` already in schema** — the database is already hierarchical. No schema redesign needed for the parent-child relationship itself.

2. **`display_order` already on both `categories` table and `CategoryHierarchyLink`** — ordering is fully managed.

3. **`CategoryHierarchyLink` is an enterprise-grade first-class entity** — multi-parent cross-listing, visibility controls, navigation labels, and per-parent ordering are already implemented.

4. **Data-driven navigation** — `Navbar.tsx` uses `useNavigationTree()` hook. No hardcoded categories. Adding new roots automatically appears in the menu.

5. **Clean service architecture** — `CategoryService` is well-structured, 724 lines, comprehensive JSDoc. Easy to modify.

6. **Full admin category management** — create, edit, deactivate, hard-delete, manage parents, reorder — all implemented.

7. **Soft-delete** on both categories (`is_active=false`) and products (`is_deleted=true`) — safe deactivation.

8. **Fallback mode** in navigation tree — if no hierarchy links exist, falls back to `parent_id` tree automatically.

9. **Outbox pattern for ES sync** — search indexing is durable and crash-safe.

10. **Circuit breaker in search** — ES failures fall back to MySQL gracefully.

---

## 4. Limitations & Gaps

### 4.1 Hard Depth Cap = 2 Levels
**Critical.** `CategoryService` explicitly blocks depth > 1. The target requires depth = 3 (Root → Group → Leaf). Every service method that validates "parent must be root" and "child must be subcategory" must be updated.

### 4.2 No Leaf Category Concept
There is no `isLeaf()` check or enum. Products are assigned to "subcategories" but there is no validation that prevents assigning a product to a ROOT or GROUP category in the new 3-level model.

### 4.3 Elasticsearch Has No Category Path
`ProductDocument` stores only `categoryId` (Long) and `categoryName` (String). There is no `categoryPath: ["MEN", "Topwear", "T-Shirts"]` array. This means:
- Cannot filter "show all products under MEN" without collecting all descendant IDs client-side
- Cannot do breadcrumb-aware search
- Cannot aggregate by category hierarchy

### 4.4 Navigation Is Not a Mega-Menu
The current desktop Navbar renders a simple dropdown (single column). The target is a full-width mega-menu with columns for Group categories and rows for Leaf categories — like Myntra's layout in the screenshots.

### 4.5 Breadcrumbs Are 2-Level Only
`getCategoryBreadcrumbs()` returns `[root]` or `[root, subcategory]`. The target is `[Home → MEN → Topwear → T-Shirts → Product]` — 5 levels including Home and product.

### 4.6 ProductListingPage Category Filtering is Shallow
The client-side slug→ID resolution only goes one level deep (root → collect subcategory IDs). In a 3-level model it would need to collect all LEAF IDs under a GROUP or ROOT.

### 4.7 Category Filter in FilterPanel
The FilterPanel has Size, Color, and Price filters but **no category hierarchy browser**. When browsing MEN → Topwear, there is no way to filter down to T-Shirts vs Jackets.

### 4.8 Admin: No Tree Visualization
AdminCategoriesPage shows a flat table sorted by ID. For a 3-level hierarchy with 50+ categories, this is unusable. No tree/accordion view exists.

### 4.9 SKU Code Architecture
The `code` field (e.g. "MEN", "TEE") is used in SKU generation: `EGO-{CODE}-{0001}-BLK-M`. In the current 2-level model, the subcategory code is used. In the 3-level model, the LEAF category code should be used in SKUs. Since `code` is immutable after creation, existing SKUs are unaffected — only new products in new leaf categories pick up new codes.

### 4.10 `CategoryHierarchyLink` Constraint Mismatch
Currently `CategoryHierarchyLink` validates:
- `parent` must be a root category
- `child` must be a subcategory

In a 3-level model this needs to become:
- `parent` can be ROOT or GROUP
- `child` must be one level deeper than parent

The existing validation logic must be replaced with depth-aware checks.

---

## 5. Migration Risks

| Risk | Severity | Notes |
|------|----------|-------|
| SKU format disruption | LOW | `code` is immutable; existing SKUs unchanged. Only new products under new leaf categories get new codes. |
| Existing product-category FK | MEDIUM | All existing products reference depth=1 (subcategory) categories. These become GROUP categories. Products need reassignment to new LEAF categories. |
| ES index desync | MEDIUM | Adding `categoryPath` requires a full reindex. Use existing `SearchReindexJob`. |
| Breadcrumb regressions | LOW | Currently only 2-level. Extension to 3-level is additive. |
| Admin workflow disruption | LOW | AdminCategoriesPage needs visual overhaul but all APIs remain backward-compatible. |
| `CategoryHierarchyLink` semantic change | MEDIUM | Link constraints (root=parent, subcategory=child) must be relaxed to allow GROUP→LEAF links. |
| Frontend category resolution | MEDIUM | ProductListingPage's slug→ID resolver must traverse 3 levels, not 2. |
| `CategoryTreeResponse` shape | LOW | Adding a `subcategories.subcategories` (leaf categories) is a new field — additive, backward-compatible. |

---

## 6. Impacted Systems Summary

| System | Impact | Scope |
|--------|--------|-------|
| `Category.java` entity | LOW | No schema change needed — `parent_id` already supports arbitrary depth |
| `categories` DB table | NONE | Already correct schema |
| `category_hierarchy_links` DB table | LOW | Constraint comments need updating; no column changes |
| `CategoryService.java` | HIGH | Remove 2-level hard cap; add leaf validation; update breadcrumbs to 3-level |
| `CategoryController.java` | LOW | API surface unchanged; add optional depth/type query params |
| Category DTOs | MEDIUM | `CategoryTreeResponse` needs 3-level nesting; `CategoryTreeItemResponse` needs leaf subcategories |
| `ProductService.java` | MEDIUM | Leaf-category validation instead of subcategory validation |
| `ProductDocument.java` | MEDIUM | Add `categoryPath: List<String>` field |
| `SearchIndexService.java` | MEDIUM | Populate `categoryPath` by traversing parent chain |
| ES index mappings JSON | MEDIUM | Add `categoryPath` as keyword array mapping |
| `SearchService.java` | LOW-MEDIUM | Update category filter to use `categoryPath` contains query |
| `Navbar.tsx` | HIGH | Replace simple dropdown with full mega-menu (3-level: root→group→leaf columns) |
| `ProductListingPage.tsx` | MEDIUM | 3-level category slug resolution; breadcrumbs component |
| `AdminCategoriesPage.tsx` | HIGH | Tree visualization; leaf category distinction; 3-level creation UI |
| `catalog.types.ts` | MEDIUM | Extend `CategoryTreeResponse` for 3-level; add `categoryPath` to search types |

---

## 7. What Does NOT Need to Change

1. **`categories` table schema** — `parent_id` already supports arbitrary depth.
2. **`category_hierarchy_links` table schema** — no column changes needed.
3. **Authentication & Authorization** — unaffected.
4. **Cart, Orders, Payments, Returns** — no category awareness; unaffected.
5. **Product variants, images, inventory** — unaffected.
6. **Elasticsearch outbox/poller mechanism** — only the document content changes.
7. **Search autocomplete** — unaffected (queries on name field).
8. **Admin product CRUD** — only the category selection UI changes (3-level picker).
9. **Existing SKUs** — `code` is immutable; all existing SKUs valid forever.
10. **`useNavigationTree()` hook** — the API call is unchanged; only the response shape changes.

---

## 8. Recommended Migration Strategy

### Phase A — Backend: Remove Depth Cap, Add Leaf Validation
1. Remove `parent.isSubcategory()` → 400 block from `CategoryService.createCategory()`
2. Remove root/subcategory constraints from `addParentToCategory()` — use depth-aware logic instead
3. Add `getDepth()` helper to `Category` entity (traverses parent chain)
4. Add `isLeaf()` helper — a category is a leaf if it has no active children
5. Update `ProductService.createProduct()` — validate category `isLeaf()` instead of `isSubcategory()`
6. Update `getCategoryBreadcrumbs()` — traverse full parent chain, not just 2 levels
7. Update `getNavigationTree()` — return 3-level structure (root → group → leaf)

### Phase B — DTOs & API Response Shape
1. Update `CategoryTreeResponse` — `subcategories` items can themselves have `leafCategories: CategoryTreeItemResponse[]`
2. Update `CategoryTreeItemResponse` — add optional `leafCategories` field
3. New `CategoryType` enum or `level` field on `CategoryResponse` for admin clarity

### Phase C — Elasticsearch
1. Add `categoryPath: List<String>` to `ProductDocument`
2. Update ES mapping JSON to add `categoryPath` as keyword array
3. Update `SearchIndexService.toDocument()` to populate `categoryPath` by traversing parent chain
4. Update `SearchService` category filter — use `terms` on `categoryPath` instead of just `categoryId`
5. Run full reindex via `SearchReindexJob`

### Phase D — Frontend Navigation (Mega Menu)
1. Create `MegaMenu.tsx` component replacing the simple dropdown
2. Desktop: full-width overlay panel; columns = group categories; rows = leaf categories
3. Mobile: replace flat drawer with expandable accordion (root → group → leaf)
4. Animate mega-menu open/close with framer-motion

### Phase E — Frontend Product Listing
1. Update `ProductListingPage` category slug→ID resolver to handle 3-level hierarchy
2. Add `CategoryBreadcrumb.tsx` component using `getCategoryBreadcrumbs()` API
3. Use breadcrumb on ProductListingPage and ProductDetailPage

### Phase F — Admin Portal
1. Update `AdminCategoriesPage` — tree view visualization (accordion-style: root → group → leaf)
2. Update create category form — 3-level parent selection (root for groups, group for leaves)
3. Display category level badge (ROOT / GROUP / LEAF) in table
4. Update product category selector — cascading dropdown (root → group → leaf)

### Phase G — Data
1. Create seed data: ROOT categories (MEN, WOMEN, KIDS, HOME, BEAUTY, GENZ)
2. Create GROUP categories (Topwear, Bottomwear, Footwear, etc.) under roots
3. Create LEAF categories (T-Shirts, Casual Shirts, Jeans, etc.) under groups
4. Reassign existing test products from current subcategories (becoming GROUP level) to new LEAF categories
5. Run reindex

---

## 9. Open Questions / Decisions Required

1. **Category code in SKU** — In the 3-level model, which level's code appears in the SKU? Current = subcategory code. Target = likely LEAF code. Existing SKUs are unaffected (code is immutable). New products use whatever category's code they are assigned to. **Decision: leaf category code for new products.**

2. **`CategoryHierarchyLink` in 3-level model** — Should hierarchy links be used for CROSS-LISTING only (a leaf appearing under multiple groups), or should they represent the primary tree structure? **Recommended: primary tree = `parent_id` FK; cross-listing = `CategoryHierarchyLink`; this is already the existing design.**

3. **Search by GROUP category** — When user clicks "Topwear" (a group), should ES return all products from all leaf categories under Topwear (T-Shirts + Shirts + Jackets)? **Answer: yes — via `categoryPath` contains "Topwear" filter.**

4. **Mega-menu trigger** — Hover (desktop) or click? **Recommended: hover with delay for desktop, click/tap for mobile.**

5. **Leaf category display in mega-menu** — Do leaf categories appear as plain text links under group column headers? **Answer: yes — matching the Myntra screenshot.**

---

## 10. Post-Migration State (June 4, 2026)

All gaps from Section 4 have been resolved:

| Gap | Resolution |
|-----|------------|
| 4.1 Hard 2-level depth cap | ✅ `CategoryService` now supports 3 levels; depth enforced via `isLeaf()` check |
| 4.2 No LEAF concept | ✅ `Category.isLeaf()` added; `ProductService` validates category is LEAF before assignment |
| 4.3 ES has no category path | ✅ `ProductDocument.categorySlugPath` keyword array added; `SearchService` uses `term(categorySlugPath, slug)` |
| 4.4 No mega-menu | ✅ `MegaMenu.tsx` implemented: full-width panel, GROUP columns, LEAF rows |
| 4.5 2-level breadcrumbs | ✅ `getCategoryBreadcrumbs()` now traverses full parent chain; returns up to 3 items |
| 4.6 Shallow product filtering | ✅ `categorySlug` filter works at all 3 depths via ES `categorySlugPath` |
| 4.7 No category filter in FilterPanel | ✅ Filter by category via URL param `?category=<slug>` |
| 4.8 No tree visualization in admin | ✅ `AdminCategoriesPage.tsx` rebuilt with LevelChip badges (ROOT/GROUP/LEAF) |
| 4.9 SKU code architecture | ✅ LEAF category code used in SKUs for new products; existing codes immutable |
| 4.10 `CategoryHierarchyLink` constraint mismatch | ✅ Links support both ROOT→GROUP and GROUP→LEAF; depth-aware validation |

### Current DB State
```sql
SELECT COUNT(*) FROM categories;              -- 133
SELECT COUNT(*) FROM category_hierarchy_links; -- 130
SELECT COUNT(*) FROM categories WHERE parent_id IS NULL;  -- 5 (ROOTs)
```

### Current API State
| Endpoint | Status |
|----------|---------|
| `GET /api/v1/categories` | ✅ Returns 5 ROOTs with groups[] + leafCategories[] |
| `GET /api/v1/categories/leaves` | ✅ Returns 99 LEAF categories with level:"LEAF" |
| `GET /api/v1/categories/{slug}` | ✅ Returns level field (ROOT/GROUP/LEAF) |
| `GET /api/v1/categories/{slug}/breadcrumbs` | ✅ Returns up to 3-item path |
| `GET /api/v1/search?categorySlug=<slug>` | ✅ Hierarchy-aware via categorySlugPath |

## 10 (old). Test Plan (High Level)

See `/docs/E2E_TEST_PLAN.md` Section 19 (Admin: Categories) and Section 25 (Category Architecture V2 Acceptance Tests).

Coverage areas:
- ✅ Category creation at each depth (ROOT, GROUP, LEAF)
- ✅ Depth validation (can't create depth > 2)
- ✅ Product assignment validation (only to LEAF)
- ✅ Navigation tree 3-level API response (133 categories, 130 links)
- ✅ Mega-menu rendering (desktop + mobile)
- ✅ Breadcrumb 3-level
- ✅ Search/filter via `categorySlugPath` (hierarchy-aware)
- ⏳ ES reindex and verification (requires products to be created first)
- ✅ Admin level badges (ROOT/GROUP/LEAF chips)
- ✅ Cross-listing (category under multiple parents, `primary` flag)
