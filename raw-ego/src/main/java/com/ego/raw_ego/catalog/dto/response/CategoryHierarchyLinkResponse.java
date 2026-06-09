package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.CategoryHierarchyLink;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response DTO for a {@link com.ego.raw_ego.catalog.entity.CategoryHierarchyLink}.
 *
 * <p>Exposes the full relationship metadata — used in admin category management
 * panels to display and control the hierarchy graph.
 */
@Data
@Builder
public class CategoryHierarchyLinkResponse {

    private Long   id;

    // ── Parent side ───────────────────────────────────────────────────────────
    private Long   parentId;
    private String parentName;
    private String parentSlug;

    // ── Child side ────────────────────────────────────────────────────────────
    private Long   childId;
    private String childName;
    private String childSlug;

    // ── Link metadata ─────────────────────────────────────────────────────────
    /** True = this link mirrors the canonical categories.parent_id FK. */
    private boolean primary;

    /** Ordering of this child within the parent's navigation section. */
    private int     displayOrder;

    /** If false, child is hidden from this parent's storefront nav tree. */
    private boolean visible;

    /**
     * Optional label override for this child in this parent's context.
     * Null = use child's own name. Non-null overrides display in the nav.
     */
    private String  navigationLabel;

    /** Effective label: navigationLabel if set, otherwise childName. */
    private String  resolvedLabel;

    private Instant createdAt;
    private Instant updatedAt;

    // ── Static factory ────────────────────────────────────────────────────────

    public static CategoryHierarchyLinkResponse from(CategoryHierarchyLink link) {
        return CategoryHierarchyLinkResponse.builder()
                .id(link.getId())
                .parentId(link.getParent().getId())
                .parentName(link.getParent().getName())
                .parentSlug(link.getParent().getSlug())
                .childId(link.getChild().getId())
                .childName(link.getChild().getName())
                .childSlug(link.getChild().getSlug())
                .primary(link.isPrimary())
                .displayOrder(link.getDisplayOrder())
                .visible(link.isVisible())
                .navigationLabel(link.getNavigationLabel())
                .resolvedLabel(link.resolvedLabel())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }
}
