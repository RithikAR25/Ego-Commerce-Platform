package com.ego.raw_ego.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a product category in the EGO catalog.
 *
 * <p><b>Depth contract (LOCKED — 3-level enterprise hierarchy):</b>
 * <ul>
 *   <li>ROOT  (depth=0): {@code parent == null}. e.g. "MEN", "WOMEN", "KIDS".</li>
 *   <li>GROUP (depth=1): {@code parent.parent == null}. e.g. "Topwear", "Bottomwear".</li>
 *   <li>LEAF  (depth=2): {@code parent.parent != null}. e.g. "T-Shirts", "Jeans".</li>
 * </ul>
 * Max depth = 2. Enforced at the service layer, not the DB.
 * <b>Products MUST be assigned only to LEAF categories.</b>
 *
 * <p>{@code code} is a short uppercase identifier (e.g. "TSH", "JNS") used as
 * the leaf segment of the SKU format: {@code EGO-{LEAF_CODE}-{PRODUCT_CODE}-{COLOR}-{SIZE}}.
 *
 * <p>{@code slug} is the URL-friendly identifier used in storefront routes:
 * {@code /products?category=t-shirts}.
 */
@Entity
@Table(
    name = "categories",
    indexes = {
        @Index(name = "idx_category_parent", columnList = "parent_id"),
        @Index(name = "uq_category_slug",    columnList = "slug",   unique = true),
        @Index(name = "uq_category_code",    columnList = "code",   unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent category — null for ROOT, non-null for GROUP and LEAF.
     * FK enforces referential integrity; depth enforcement is in CategoryService.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /**
     * Child categories — for ROOT: contains GROUP children.
     *                    for GROUP: contains LEAF children.
     *                    for LEAF:  always empty.
     * Loaded lazily to avoid N+1 on listing queries.
     */
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Category> children = new ArrayList<>();

    /** Display name, e.g. "Oversized Tees". */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Short uppercase code used in SKU generation.
     * LEAF category code is the SKU segment (e.g. "TSH" for T-Shirts).
     * Immutable after creation — changing would break all existing SKUs.
     */
    @Column(nullable = false, length = 10, unique = true)
    private String code;

    /**
     * URL-friendly identifier, e.g. "t-shirts".
     * Used in storefront filter routes. Always unique.
     */
    @Column(nullable = false, length = 150, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Cloudinary URL for the category image (used on category listing pages). */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Controls the display order in the navigation menu. Lower = first. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Depth helpers ─────────────────────────────────────────────────────────

    /**
     * Returns the depth of this category in the hierarchy.
     * ROOT=0, GROUP=1, LEAF=2.
     * Traverses the parent chain — O(depth), which is always ≤ 2.
     */
    public int getDepth() {
        int depth = 0;
        Category current = this.parent;
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    /** @return true if this is a ROOT category (no parent). */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * @return true if this is a GROUP category (depth=1).
     *         GROUP categories appear under ROOT in the navigation tree.
     *         Products may NOT be assigned to GROUP categories.
     */
    public boolean isGroup() {
        return parent != null && parent.getParent() == null;
    }

    /**
     * @return true if this is a LEAF category (depth=2).
     *         LEAF categories are the only valid assignment targets for products.
     *         They appear as the lowest level in the navigation tree.
     */
    public boolean isLeaf() {
        return parent != null && parent.getParent() != null;
    }
}
