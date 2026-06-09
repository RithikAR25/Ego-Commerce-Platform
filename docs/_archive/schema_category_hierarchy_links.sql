-- =============================================================================
-- EGO Platform — Category Hierarchy Links Migration
-- Phase: Category Taxonomy Evolution (Enterprise Architecture)
-- Date: 2026-05-29
-- Author: Antigravity (auto-generated)
-- =============================================================================
-- PURPOSE:
--   Introduces the category_hierarchy_links table which replaces the single-parent
--   model with an enterprise first-class relationship entity.
--
-- STRATEGY: Zero-downtime, additive migration
--   1. Create the new table (non-breaking — existing code continues to work)
--   2. Backfill from categories.parent_id (existing parent relationships preserved)
--   3. Deploy new backend code (reads from hierarchy_links + parent_id fallback)
--   4. Verify navigation tree renders correctly
--   5. Run the Hoodies cross-listing steps below
--
-- ROLLBACK:
--   DROP TABLE IF EXISTS category_hierarchy_links;
--   (All existing product/category relationships in the categories table remain untouched.)
-- =============================================================================

-- STEP 1: Create the hierarchy links table
-- Note: Hibernate ddl-auto=update will auto-create this from the entity on startup.
-- This script is provided for manual migration in environments where Hibernate
-- schema update is disabled or for audit/documentation purposes.

CREATE TABLE IF NOT EXISTS category_hierarchy_links (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    parent_category_id  BIGINT          NOT NULL,
    child_category_id   BIGINT          NOT NULL,
    is_primary          TINYINT(1)      NOT NULL DEFAULT 0,
    display_order       INT             NOT NULL DEFAULT 0,
    is_visible          TINYINT(1)      NOT NULL DEFAULT 1,
    navigation_label    VARCHAR(100)    DEFAULT NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_hierarchy_parent_child (parent_category_id, child_category_id),
    KEY idx_hierarchy_parent (parent_category_id),
    KEY idx_hierarchy_child  (child_category_id),

    CONSTRAINT fk_hierarchy_parent
        FOREIGN KEY (parent_category_id)
        REFERENCES categories (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_hierarchy_child
        FOREIGN KEY (child_category_id)
        REFERENCES categories (id)
        ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Enterprise category taxonomy — first-class parent-child relationship entity.';


-- =============================================================================
-- STEP 2: Backfill from existing categories.parent_id
-- Copies all existing single-parent relationships into the new table.
-- All backfilled links are marked is_primary=1 (canonical).
-- display_order matches the child category''s own display_order column.
-- =============================================================================

INSERT INTO category_hierarchy_links (
    parent_category_id,
    child_category_id,
    is_primary,
    display_order,
    is_visible,
    navigation_label
)
SELECT
    c.parent_id        AS parent_category_id,
    c.id               AS child_category_id,
    1                  AS is_primary,       -- canonical (mirrors parent_id FK)
    c.display_order    AS display_order,
    1                  AS is_visible,       -- visible by default
    NULL               AS navigation_label  -- use child's own name
FROM categories c
WHERE c.parent_id IS NOT NULL             -- only subcategories have parents
ON DUPLICATE KEY UPDATE
    is_primary    = VALUES(is_primary),
    display_order = VALUES(display_order);
-- ON DUPLICATE KEY UPDATE makes this script re-runnable (idempotent).


-- =============================================================================
-- STEP 3: Cross-list Hoodies under Women
-- (The primary business requirement that triggered this migration)
--
-- Hoodies (id=5) was only under Men (id=1).
-- This adds it to Women (id=2) as a non-primary cross-listing.
-- =============================================================================

-- Find the actual IDs first:
-- SELECT id, name, parent_id FROM categories;
-- Then adjust the IDs below if they differ from 5 and 2.

INSERT INTO category_hierarchy_links (
    parent_category_id,
    child_category_id,
    is_primary,
    display_order,
    is_visible,
    navigation_label
)
VALUES (
    2,     -- Women (root) — id=2 from the screenshot
    5,     -- Hoodies (subcategory) — id=5 from the screenshot
    0,     -- NOT primary (Men remains canonical)
    0,     -- display_order within Women's nav
    1,     -- visible
    NULL   -- use child's own name "Hoodies"
)
ON DUPLICATE KEY UPDATE
    is_visible    = 1,
    navigation_label = NULL;
-- Safe to re-run — ON DUPLICATE KEY UPDATE is a no-op if already inserted.


-- =============================================================================
-- STEP 4: Verification queries — run AFTER applying the migration
-- =============================================================================

-- 4a. Verify table structure
-- DESCRIBE category_hierarchy_links;

-- 4b. Verify all backfilled links
-- SELECT
--     p.name AS parent,
--     c.name AS child,
--     l.is_primary,
--     l.display_order,
--     l.is_visible,
--     l.navigation_label
-- FROM category_hierarchy_links l
-- JOIN categories p ON p.id = l.parent_category_id
-- JOIN categories c ON c.id = l.child_category_id
-- ORDER BY p.display_order, l.display_order;

-- 4c. Verify Hoodies appears under BOTH Men and Women
-- SELECT
--     p.name AS parent,
--     c.name AS child,
--     l.is_primary
-- FROM category_hierarchy_links l
-- JOIN categories p ON p.id = l.parent_category_id
-- JOIN categories c ON c.id = l.child_category_id
-- WHERE c.name = 'Hoodies'
-- ORDER BY l.is_primary DESC;
-- Expected: 2 rows — Men (is_primary=1) and Women (is_primary=0)

-- 4d. Count: should match number of subcategories + 1 for the Hoodies cross-listing
-- SELECT COUNT(*) FROM category_hierarchy_links;
-- SELECT COUNT(*) FROM categories WHERE parent_id IS NOT NULL;  -- should be COUNT(*) - 1


-- =============================================================================
-- ROLLBACK STRATEGY
-- =============================================================================
-- To undo this entire migration:
--
-- Step 1: Revert to old backend code (which does not read from hierarchy_links)
-- Step 2: DROP TABLE IF EXISTS category_hierarchy_links;
--
-- The categories table is unchanged.
-- No products are affected.
-- The original parent_id relationships remain intact throughout.
-- This migration is fully non-destructive.
-- =============================================================================
