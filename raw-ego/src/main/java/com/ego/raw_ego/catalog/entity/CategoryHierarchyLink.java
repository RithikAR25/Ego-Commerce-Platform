package com.ego.raw_ego.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * First-class relationship entity representing a parent-child link in the EGO
 * category taxonomy.
 *
 * <p><b>Why a first-class entity instead of @ManyToMany?</b><br>
 * A raw join table cannot carry metadata. This entity allows enterprise-grade
 * controls ON the relationship itself:
 * <ul>
 *   <li>{@code isPrimary}       — canonical breadcrumb path (mirrors parent_id FK)</li>
 *   <li>{@code displayOrder}    — per-parent ordering (Hoodies may be #1 under Men, #3 under Women)</li>
 *   <li>{@code isVisible}       — hide from nav without deactivating the subcategory</li>
 *   <li>{@code navigationLabel} — override display name in this parent's context
 *                                  (e.g. "Unisex" under Accessories)</li>
 * </ul>
 *
 * <p><b>Relationship semantics:</b>
 * <pre>
 *   parent (root category, parent_id = NULL)
 *       └── child (subcategory, parent_id = canonical root)
 * </pre>
 * Each row represents "child appears under parent in the navigation tree."
 * A child may have multiple rows (one per root it belongs to).
 *
 * <p><b>Constraints enforced at DB level:</b>
 * <ul>
 *   <li>UNIQUE (parent_category_id, child_category_id) — no duplicate links</li>
 *   <li>parent_category_id ≠ child_category_id — no self-parenting</li>
 *   <li>FK ON DELETE RESTRICT on both sides — audit-safe deletion</li>
 * </ul>
 *
 * <p><b>Constraints enforced at service level:</b>
 * <ul>
 *   <li>parent must be a root category (parent.isRoot() == true)</li>
 *   <li>child must be a subcategory (child.isSubcategory() == true)</li>
 *   <li>parent must be active</li>
 *   <li>child must retain at least one link after removal</li>
 *   <li>no circular references possible (depth is capped at 1)</li>
 * </ul>
 *
 * <p><b>Future extensibility:</b> Additional columns can be added without
 * schema refactoring — e.g. {@code campaign_id}, {@code visible_from},
 * {@code visible_until}, {@code store_id} for multi-storefront support.
 */
@Entity
@Table(
    name = "category_hierarchy_links",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_hierarchy_parent_child",
            columnNames = {"parent_category_id", "child_category_id"}
        )
    },
    indexes = {
        @Index(name = "idx_hierarchy_parent", columnList = "parent_category_id"),
        @Index(name = "idx_hierarchy_child",  columnList = "child_category_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryHierarchyLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The parent root category in this relationship.
     * Must always be a root category (parent.parent == null).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id", nullable = false)
    private Category parent;

    /**
     * The child subcategory being linked.
     * Must always be a subcategory (child.parent != null).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_category_id", nullable = false)
    private Category child;

    /**
     * If true, this link represents the canonical parent relationship —
     * matching the {@code categories.parent_id} FK.
     * Used for breadcrumb generation and legacy compatibility.
     * Exactly one link per child must have isPrimary = true.
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    /**
     * Display order of this child within the parent's navigation section.
     * Allows Hoodies to be position #1 under Men but #3 under Women.
     * Defaults to the child's own {@code displayOrder}.
     */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    /**
     * If false, the child is hidden from this parent's navigation tree
     * without deactivating the subcategory entirely.
     * Useful for seasonal or campaign-specific visibility.
     */
    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private boolean visible = true;

    /**
     * Optional override label for this child within this parent's context.
     * If null, the child's own {@code name} is used.
     * Example: "Hoodies" may be labelled "Unisex Hoodies" under Accessories.
     */
    @Column(name = "navigation_label", length = 100)
    private String navigationLabel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Convenience ───────────────────────────────────────────────────────────

    /**
     * @return the effective label to display in navigation — override if set, else child's name.
     */
    public String resolvedLabel() {
        return (navigationLabel != null && !navigationLabel.isBlank())
               ? navigationLabel
               : child.getName();
    }
}
