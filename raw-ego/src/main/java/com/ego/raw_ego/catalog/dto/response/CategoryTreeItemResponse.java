package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.Category;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single entry within a parent category's navigation section.
 *
 * <p>Used at two levels in the 3-level tree:
 * <ul>
 *   <li><b>GROUP level:</b> direct child of a ROOT. Carries its LEAF categories in {@code leafCategories}.</li>
 *   <li><b>LEAF level:</b> child of a GROUP. {@code leafCategories} is always empty.</li>
 * </ul>
 *
 * <p>Link-level metadata (displayOrder, visible, resolvedLabel, primary) comes from
 * the {@link com.ego.raw_ego.catalog.entity.CategoryHierarchyLink} row, NOT from
 * the category's own global displayOrder field. This allows per-parent ordering
 * (e.g. "Hoodies" at position 1 under MEN but position 3 under WOMEN).
 */
@Data
@Builder
public class CategoryTreeItemResponse {

    private Long    id;
    private String  name;
    private String  code;
    private String  slug;
    private String  imageUrl;

    /**
     * Display order within THIS parent's navigation section.
     * Sourced from the hierarchy link, not the category's global displayOrder.
     */
    private int     displayOrder;

    /**
     * Whether this item is visible in THIS parent's navigation section.
     * Hidden items exist in the DB but are not rendered in the nav.
     */
    private boolean visible;

    /**
     * Effective navigation label.
     * The navigationLabel override from the hierarchy link if set,
     * otherwise the category's own name.
     */
    private String  resolvedLabel;

    /**
     * True if this is the canonical parent link.
     * The canonical parent governs breadcrumbs and SKU code derivation.
     */
    private boolean primary;

    /**
     * LEAF categories under this GROUP item.
     * Populated only when this item is at GROUP level (depth=1).
     * Always empty for LEAF items themselves.
     */
    @Builder.Default
    private List<CategoryTreeItemResponse> leafCategories = new ArrayList<>();

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Builds a leaf-level item from a hierarchy link.
     * {@code leafCategories} is empty (LEAFs have no children).
     */
    public static CategoryTreeItemResponse from(
            com.ego.raw_ego.catalog.entity.CategoryHierarchyLink link) {
        Category child = link.getChild();
        return CategoryTreeItemResponse.builder()
                .id(child.getId())
                .name(child.getName())
                .code(child.getCode())
                .slug(child.getSlug())
                .imageUrl(child.getImageUrl())
                .displayOrder(link.getDisplayOrder())
                .visible(link.isVisible())
                .resolvedLabel(link.resolvedLabel())
                .primary(link.isPrimary())
                .leafCategories(new ArrayList<>())
                .build();
    }

    /**
     * Builds a GROUP-level item from a hierarchy link and its LEAF children.
     */
    public static CategoryTreeItemResponse fromGroup(
            com.ego.raw_ego.catalog.entity.CategoryHierarchyLink link,
            List<CategoryTreeItemResponse> leafCategories) {
        Category child = link.getChild();
        return CategoryTreeItemResponse.builder()
                .id(child.getId())
                .name(child.getName())
                .code(child.getCode())
                .slug(child.getSlug())
                .imageUrl(child.getImageUrl())
                .displayOrder(link.getDisplayOrder())
                .visible(link.isVisible())
                .resolvedLabel(link.resolvedLabel())
                .primary(link.isPrimary())
                .leafCategories(leafCategories)
                .build();
    }
}
