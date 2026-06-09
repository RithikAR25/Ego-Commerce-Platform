package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for adding a parent root category to an existing subcategory.
 *
 * <p>Posted to: {@code POST /api/v1/admin/categories/{childId}/parents}
 *
 * <p><b>Validations enforced at service layer (not DTO level):</b>
 * <ul>
 *   <li>parentId must reference an existing, ACTIVE root category</li>
 *   <li>childId must reference an existing subcategory (not a root)</li>
 *   <li>link must not already exist (idempotent: returns existing if duplicate)</li>
 *   <li>cannot self-parent</li>
 * </ul>
 */
@Data
public class AddHierarchyLinkRequest {

    /** The root category to add as a parent. */
    @NotNull(message = "parentId is required")
    private Long parentId;

    /**
     * Display order of this child within the parent's navigation section.
     * Defaults to 0 (first position).
     */
    @Min(value = 0, message = "displayOrder must be >= 0")
    private int displayOrder = 0;

    /**
     * Whether this child is visible in the parent's navigation section.
     * Defaults to true.
     */
    private boolean visible = true;

    /**
     * Optional label override for this child in this parent's navigation context.
     * If null or blank, the child's own name is used.
     * Example: set to "Unisex Hoodies" when linking Hoodies under Accessories.
     */
    @Size(max = 100, message = "navigationLabel must not exceed 100 characters")
    private String navigationLabel;
}
