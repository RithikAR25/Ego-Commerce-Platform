package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Check if a slug is already taken (for uniqueness validation on create). */
    boolean existsBySlug(String slug);

    /**
     * Check if a slug is taken by ANY category OTHER than the one with the given ID.
     * Used during category updates so a category may keep its own slug unchanged.
     */
    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Check if a code is already taken (for SKU segment uniqueness). */
    boolean existsByCode(String code);

    /**
     * True if any category has the given category as its parent.
     * Used before hard-delete to ensure a ROOT has no GROUP children,
     * or a GROUP has no LEAF children.
     */
    boolean existsByParentId(Long parentId);

    /** Find by URL slug — used for storefront routing. */
    Optional<Category> findBySlugAndActiveTrue(String slug);

    /** All active ROOT categories (parent is null). */
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.active = true ORDER BY c.displayOrder ASC")
    List<Category> findAllRootCategoriesActive();

    /**
     * All active GROUP categories (depth=1: parent is ROOT, parent.parent is null).
     * Used for admin category pickers and navigation tree construction.
     */
    @Query("""
        SELECT c FROM Category c
        WHERE c.parent IS NOT NULL
          AND c.parent.parent IS NULL
          AND c.active = true
        ORDER BY c.parent.displayOrder ASC, c.displayOrder ASC
        """)
    List<Category> findAllGroupCategoriesActive();

    /**
     * All active LEAF categories (depth=2: parent is GROUP, parent.parent is ROOT).
     * Products MUST be assigned only to LEAF categories.
     * Used by admin product creation form and product validation.
     */
    @Query("""
        SELECT c FROM Category c
        WHERE c.parent IS NOT NULL
          AND c.parent.parent IS NOT NULL
          AND c.active = true
        ORDER BY c.parent.parent.displayOrder ASC, c.parent.displayOrder ASC, c.displayOrder ASC
        """)
    List<Category> findAllLeafCategoriesActive();
}
