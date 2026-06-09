package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for creating a new product.
 *
 * <p>The product is created in DRAFT status by default.
 * To publish, use the PATCH /admin/products/{id}/status endpoint.
 *
 * <p>{@code categoryId} must reference a SUBCATEGORY (depth=1), not a root category.
 * This is validated in ProductService.
 */
@Data
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String name;

    @NotNull(message = "Category is required")
    private Long categoryId;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @Size(max = 255, message = "Material must not exceed 255 characters")
    private String material;

    @Size(max = 2000, message = "Care instructions must not exceed 2000 characters")
    private String careInstructions;

    /** Optional tags for search — e.g. ["streetwear", "oversized"]. */
    private List<String> tags;
}
