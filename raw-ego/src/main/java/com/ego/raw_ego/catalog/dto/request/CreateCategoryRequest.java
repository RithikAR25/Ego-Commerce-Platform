package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * Request body for creating a new category (root or subcategory).
 *
 * <p>If {@code parentIds} is null or empty → creates a root category (e.g. "Men").
 * If {@code parentIds} has one or more entries → creates a subcategory linked to
 * each specified root. The first entry in the list becomes the canonical primary
 * parent (mirrors {@code categories.parent_id}).
 *
 * <p>Max depth = 1 (root → subcategory) is enforced in CategoryService.
 * All parentIds must reference active root categories.
 *
 * <p><b>Multi-parent example (unisex subcategory):</b>
 * <pre>
 * {
 *   "name": "Hoodies",
 *   "code": "HOD",
 *   "parentIds": [1, 2]   // Men (canonical) + Women (cross-listed)
 * }
 * </pre>
 */
@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    private String name;

    /**
     * Short uppercase code for SKU generation and internal reference.
     * e.g. "TEE", "HOD", "CGO", "MEN", "WOM"
     */
    @NotBlank(message = "Category code is required")
    @Pattern(regexp = "^[A-Z]{2,10}$", message = "Category code must be 2-10 uppercase letters")
    private String code;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private String imageUrl;

    /**
     * Parent category IDs.
     * <ul>
     *   <li>Null or empty → root category</li>
     *   <li>One entry    → subcategory with a single parent</li>
     *   <li>Multiple     → unisex/cross-listed subcategory; first entry is canonical</li>
     * </ul>
     * All referenced IDs must be active root categories.
     */
    @Size(max = 10, message = "A subcategory may not have more than 10 parents")
    private List<Long> parentIds;

    private Integer displayOrder = 0;
}
