package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for creating a new attribute type on a product.
 *
 * <p>Example: name="Color" or name="Size"
 *
 * <p>Names are case-insensitive and must be unique per product.
 * The backend validates this before persisting.
 */
@Data
public class CreateAttributeTypeRequest {

    @NotBlank(message = "Attribute type name is required")
    @Size(max = 50, message = "Attribute type name must not exceed 50 characters")
    private String name;

    /** Controls display order in the variant selector UI. Lower = first. */
    private Integer displayOrder = 0;
}
