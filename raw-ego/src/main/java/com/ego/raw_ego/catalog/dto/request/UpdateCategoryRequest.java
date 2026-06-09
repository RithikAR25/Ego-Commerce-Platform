package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * PATCH request for editing an existing category's display metadata.
 *
 * <p>Posted to: {@code PATCH /api/v1/admin/categories/{id}}
 *
 * <p><b>Intentionally excluded fields:</b>
 * <ul>
 *   <li>{@code code} — immutable after creation; baked into every SKU. A code
 *       change would silently invalidate all variant SKUs for products in this
 *       category. Blocked at the API level.</li>
 * </ul>
 *
 * <p><b>Slug behaviour:</b>
 * <ul>
 *   <li>If {@code slug} is explicitly provided → used as-is (after uniqueness check).</li>
 *   <li>If {@code slug} is null AND {@code name} changed → slug is auto-regenerated
 *       from the new name via {@link com.ego.raw_ego.common.util.SlugUtils}.</li>
 *   <li>If neither changes → slug stays unchanged.</li>
 * </ul>
 *
 * <p><b>SEO note:</b> changing a slug breaks the old URL. The service logs a
 * WARN when the slug is regenerated. Old-slug redirect support is planned for Phase 11.
 */
@Data
public class UpdateCategoryRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    @NotBlank(message = "Name cannot be blank")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    private String imageUrl;

    @Min(value = 0, message = "displayOrder must be >= 0")
    private Integer displayOrder;

    /**
     * Explicit slug override — URL-safe, lowercase, hyphen-separated.
     * If null, slug is auto-generated from {@code name}.
     * Must be unique across all categories (excluding this category itself).
     */
    @Pattern(
        regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
        message = "Slug must be lowercase, alphanumeric, hyphen-separated (e.g. 'oversized-tees')"
    )
    @Size(max = 150, message = "Slug must not exceed 150 characters")
    private String slug;
}
