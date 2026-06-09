# ADR-001: Category Architecture — 3-Level Hierarchy (ROOT → GROUP → LEAF)

**Status:** Accepted  
**Date:** June 4, 2026  
**Decision-makers:** Engineering Team  
**Implemented by:** Antigravity AI Agent

---

## Context

The EGO platform initially implemented a 2-level category hierarchy (Root → Subcategory). This matched common simple e-commerce patterns but was insufficient for the target product taxonomy inspired by Myntra/AJIO-style fashion retail, which requires:

```
ROOT    → MEN, WOMEN, KIDS, HOME, BEAUTY
GROUP   → Topwear, Bottomwear, Footwear, Accessories
LEAF    → T-Shirts, Casual Shirts, Sneakers, Jeans
```

Products in fashion retail are meaningfully differentiated at the LEAF level. Displaying "Men's Products" is not useful — "Men's T-Shirts" is. This requires a 3-level tree where products are assigned only to LEAF nodes.

---

## Decision

Implement a **3-level category hierarchy** (ROOT → GROUP → LEAF) with the following properties:

1. **Schema unchanged** — `categories.parent_id` FK already supports arbitrary depth. No DDL migration needed.
2. **Depth enforced at service layer** — `CategoryService` validates max depth = 2 (0-indexed LEAF). DB allows arbitrary depth but service blocks it.
3. **Products assigned to LEAF only** — `ProductService.createProduct()` validates `category.isLeaf()` before accepting the assignment.
4. **Category level detection** — `Category.isLeaf()` = no active children. `Category.isRoot()` = parent == null. `Category.isGroup()` = neither.
5. **`CategoryHierarchyLink` extended** — supports both ROOT→GROUP and GROUP→LEAF cross-listing (a LEAF can appear under multiple GROUPs).
6. **Breadcrumbs** — `getCategoryBreadcrumbs()` traverses full parent chain (up to 3 items: ROOT → GROUP → LEAF).
7. **Elasticsearch** — `ProductDocument` adds `categoryPath` (display names) and `categorySlugPath` (URL slugs) keyword arrays. `term(categorySlugPath, "men")` returns ALL products under MEN at any depth.
8. **Frontend** — `MegaMenu.tsx` replaced simple dropdown with a full-width mega-menu panel. ROOT = tab, GROUP = column header, LEAF = clickable row.

---

## Alternatives Considered

### A. Separate `GroupCategory` entity
- **Rejected:** Would require DB migration, new FK relationships, and breaks the existing `CategoryHierarchyLink` architecture. Over-engineering for a depth=3 requirement.

### B. Arbitrary depth tree (unlimited nesting)
- **Rejected:** Fashion retail does not require depth > 3. Unlimited depth complicates breadcrumb rendering, mega-menu layout, and ES category path arrays. Constrain to 3 for UX and simplicity.

### C. Dedicated `category_type` ENUM column
- **Evaluated but simplified:** An enum column was considered for ROOT/GROUP/LEAF. The `isLeaf()` / `isRoot()` computed properties are cleaner — they derive from tree structure (no children = leaf, no parent = root). Avoids data denormalization.

---

## Consequences

### Positive
- Richer category navigation for users (mega-menu)
- Hierarchy-aware search and filtering at any level
- SEO-friendly breadcrumbs to 3 levels
- Admin UI shows ROOT/GROUP/LEAF badges for clarity

### Negative / Risks
- Existing products (assigned to what are now GROUP categories) needed reassignment to new LEAF categories
- Elasticsearch required a full reindex to populate `categoryPath` and `categorySlugPath`
- `CategoryHierarchyLink` constraint semantics changed — link parent can now be ROOT or GROUP

### Migration Notes
- 133 categories seeded (5 ROOTs, ~28 GROUPs, ~100 LEAFs)
- 130 hierarchy links created
- Full ES reindex completed post-migration
- All CAT-NAV, CAT-LEAF, NAV-UI, BRD-UI tests passed (June 6, 2026)

---

## Source References
- `com.ego.raw_ego.catalog.entity.Category` — `isLeaf()`, `isRoot()`, `isGroup()` methods
- `com.ego.raw_ego.catalog.service.CategoryService` — navigation tree, breadcrumbs
- `com.ego.raw_ego.search.document.ProductDocument` — `categoryPath`, `categorySlugPath` fields
- `docs/database/01_category_seed.sql` — seeded 3-level taxonomy
- `docs/CATEGORY_ARCHITECTURE_AUDIT.md` — archived pre-migration baseline
