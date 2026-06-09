package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.CategoryHierarchyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link CategoryHierarchyLink} — the enterprise category taxonomy table.
 *
 * <p>All queries follow the no-N+1 principle: JOIN FETCH is used wherever
 * the caller needs to traverse related entities.
 */
@Repository
public interface CategoryHierarchyLinkRepository extends JpaRepository<CategoryHierarchyLink, Long> {

    // ── Navigation tree queries ───────────────────────────────────────────────

    /**
     * Fetches all visible links (parent + child eager-loaded) for building the
     * 3-level navigation tree in a single query.
     *
     * <p>Includes both ROOT→GROUP links and GROUP→LEAF links.
     * Only includes links where parent is active, child is active, and link is visible.
     *
     * <p>Ordered by parent.displayOrder ASC (ROOT order), then link.displayOrder ASC
     * (GROUP/LEAF order within their parent) for stable rendering.
     */
    @Query("""
        SELECT l FROM CategoryHierarchyLink l
        JOIN FETCH l.parent p
        JOIN FETCH l.child c
        WHERE p.active = true
          AND c.active = true
          AND l.visible = true
        ORDER BY p.displayOrder ASC, l.displayOrder ASC, c.displayOrder ASC
        """)
    List<CategoryHierarchyLink> findAllVisibleForNavigation();

    /**
     * Fetches all links for a given parent — used for admin category management
     * and mega-menu generation. Includes hidden links (visible=false).
     */
    @Query("""
        SELECT l FROM CategoryHierarchyLink l
        JOIN FETCH l.child c
        WHERE l.parent.id = :parentId
        ORDER BY l.displayOrder ASC, c.displayOrder ASC
        """)
    List<CategoryHierarchyLink> findAllByParentId(@Param("parentId") Long parentId);

    /**
     * Fetches all parent links for a given child — used for breadcrumb generation
     * and the admin "manage parents" UI. Primary link first.
     */
    @Query("""
        SELECT l FROM CategoryHierarchyLink l
        JOIN FETCH l.parent p
        WHERE l.child.id = :childId
        ORDER BY l.primary DESC, p.displayOrder ASC
        """)
    List<CategoryHierarchyLink> findAllByChildId(@Param("childId") Long childId);

    // ── Existence / uniqueness checks ─────────────────────────────────────────

    /** True if a link between this parent and child already exists. */
    boolean existsByParentIdAndChildId(Long parentId, Long childId);

    /**
     * Finds the specific link between a parent and child.
     * Used for idempotency guards and update operations.
     */
    Optional<CategoryHierarchyLink> findByParentIdAndChildId(Long parentId, Long childId);

    /** Count of active links for a given child — guards against orphaning. */
    @Query("SELECT COUNT(l) FROM CategoryHierarchyLink l WHERE l.child.id = :childId")
    long countByChildId(@Param("childId") Long childId);

    /** True if a primary link already exists for this child. */
    @Query("SELECT COUNT(l) > 0 FROM CategoryHierarchyLink l WHERE l.child.id = :childId AND l.primary = true")
    boolean hasPrimaryLink(@Param("childId") Long childId);

    // ── Mutation helpers ──────────────────────────────────────────────────────

    /**
     * Clears the primary flag on ALL links for a given child.
     * Called before setting a new primary link to maintain the "exactly one primary" invariant.
     */
    @Modifying
    @Query("UPDATE CategoryHierarchyLink l SET l.primary = false WHERE l.child.id = :childId")
    void clearPrimaryFlagForChild(@Param("childId") Long childId);

    /**
     * Deletes the specific link between a parent and child.
     * Caller must verify the child retains at least one other link before calling this.
     */
    @Modifying
    @Query("DELETE FROM CategoryHierarchyLink l WHERE l.parent.id = :parentId AND l.child.id = :childId")
    void deleteByParentIdAndChildId(@Param("parentId") Long parentId, @Param("childId") Long childId);

    /**
     * Deletes ALL hierarchy links that involve the given category ID
     * either as a parent or as a child.
     *
     * <p>Called as the first step of a hard-delete to clean up the FK-constrained
     * {@code category_hierarchy_links} table before the category row is removed.
     */
    @Modifying
    @Query("DELETE FROM CategoryHierarchyLink l WHERE l.parent.id = :categoryId OR l.child.id = :categoryId")
    void deleteAllByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Fetches all visible links for a specific parent — used for breadcrumb-aware
     * category tree navigation (e.g. "Women > Hoodies" context).
     */
    @Query("""
        SELECT l FROM CategoryHierarchyLink l
        JOIN FETCH l.child c
        WHERE l.parent.id = :parentId
          AND l.visible = true
          AND c.active  = true
        ORDER BY l.displayOrder ASC
        """)
    List<CategoryHierarchyLink> findVisibleByParentId(@Param("parentId") Long parentId);
}
