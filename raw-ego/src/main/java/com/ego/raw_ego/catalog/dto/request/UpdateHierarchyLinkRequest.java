package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for updating the metadata on an existing hierarchy link.
 *
 * <p>Patched to: {@code PATCH /api/v1/admin/categories/{childId}/parents/{parentId}}
 *
 * <p>All fields are optional — only non-null fields are applied (PATCH semantics).
 * This allows, for example, toggling visibility without changing the label.
 */
@Data
public class UpdateHierarchyLinkRequest {

    /**
     * New display order for this child within the parent's navigation section.
     * Null = no change.
     */
    @Min(value = 0, message = "displayOrder must be >= 0")
    private Integer displayOrder;

    /**
     * New visibility for this child in this parent's navigation tree.
     * Null = no change.
     */
    private Boolean visible;

    /**
     * New navigation label override. Empty string or null clears the override
     * and falls back to the child's own name.
     */
    @Size(max = 100, message = "navigationLabel must not exceed 100 characters")
    private String navigationLabel;
}
