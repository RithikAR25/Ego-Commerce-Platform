package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for creating a new attribute value under an attribute type.
 *
 * <p>Examples:
 * <ul>
 *   <li>Under "Color": value="Black", code="BLK", hexColor="#1A1A1A"</li>
 *   <li>Under "Size": value="Medium", code="M"</li>
 * </ul>
 *
 * <p>{@code code} becomes part of the immutable SKU. Keep it short,
 * uppercase, and stable — it cannot be changed after variants reference it.
 */
@Data
public class CreateAttributeValueRequest {

    @NotBlank(message = "Value label is required")
    @Size(max = 100, message = "Value must not exceed 100 characters")
    private String value;

    /**
     * Short uppercase code used in SKU generation.
     * Examples: BLK, WHT, RED, M, L, XL
     */
    @NotBlank(message = "Code is required")
    @Size(max = 10, message = "Code must not exceed 10 characters")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Code must be uppercase letters and digits only")
    private String code;

    /** Controls display order in the variant selector UI. */
    private Integer displayOrder = 0;

    /**
     * CSS hex color for the swatch UI — null for size attributes.
     * Example: "#1A1A1A" for Black.
     */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "hexColor must be a valid hex color, e.g. #1A1A1A")
    private String hexColor;

    /**
     * Cloudinary URL for pattern/texture swatches.
     * Use when a plain hex color is insufficient (e.g. camouflage, tie-dye).
     */
    @Size(max = 500)
    private String swatchImageUrl;
}
