package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.Category;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

/**
 * Public-facing category response.
 *
 * <p>The {@code parent} field provides the canonical primary parent (for GROUP and LEAF categories).
 * ROOT categories have {@code parent=null}.
 *
 * <p>The {@code level} field encodes the category's depth as a human-readable string:
 * {@code "ROOT"}, {@code "GROUP"}, or {@code "LEAF"}. Used by the admin portal
 * to display the appropriate badge and control which operations are available.
 *
 * <p><b>Clean-slate architecture note:</b> The legacy {@code parents[]} (plural) multi-parent
 * field has been removed. Parent context is served through the hierarchy link endpoints.
 */
@Data
@Builder
public class CategoryResponse {

    private Long    id;
    private String  name;
    private String  code;
    private String  slug;
    private String  description;
    private String  imageUrl;
    private Integer displayOrder;
    private boolean active;

    /**
     * Depth level of this category.
     * {@code "ROOT"} (depth=0), {@code "GROUP"} (depth=1), {@code "LEAF"} (depth=2).
     */
    private String level;

    /**
     * Number of products directly assigned to this category.
     * Meaningful only for LEAF categories (products cannot be assigned to ROOT or GROUP).
     * Populated only on the admin listing endpoint; storefront callers receive 0.
     */
    @Setter
    @Builder.Default
    private long productCount = 0;

    /**
     * Canonical primary parent — null for ROOT categories.
     * For GROUP: the ROOT parent. For LEAF: the GROUP parent.
     * Populated only up to one level to prevent recursive serialization.
     */
    private CategoryResponse parent;

    // ── Static factory ─────────────────────────────────────────────────────────

    /**
     * Builds from a Category entity without recursing more than one level.
     */
    public static CategoryResponse from(Category category) {
        // Build parent stub (one level only — no further recursion)
        CategoryResponse parentResponse = null;
        if (category.getParent() != null) {
            Category parentEntity = category.getParent();
            parentResponse = CategoryResponse.builder()
                    .id(parentEntity.getId())
                    .name(parentEntity.getName())
                    .code(parentEntity.getCode())
                    .slug(parentEntity.getSlug())
                    .imageUrl(parentEntity.getImageUrl())
                    .displayOrder(parentEntity.getDisplayOrder())
                    .active(parentEntity.isActive())
                    .level(resolveLevel(parentEntity))
                    .build();
        }

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .code(category.getCode())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .displayOrder(category.getDisplayOrder())
                .active(category.isActive())
                .level(resolveLevel(category))
                .parent(parentResponse)
                .build();
    }

    private static String resolveLevel(Category category) {
        if (category.isLeaf())  return "LEAF";
        if (category.isGroup()) return "GROUP";
        return "ROOT";
    }
}
