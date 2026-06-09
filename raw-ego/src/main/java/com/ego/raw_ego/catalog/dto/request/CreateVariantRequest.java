package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for creating a new product variant.
 *
 * <p>The SKU is generated server-side — never provided by the client.
 * Format: EGO-{CATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE}
 *
 * <p>{@code colorAttributeValueId} and {@code sizeAttributeValueId} must be
 * attribute values that belong to the target product. Validated in ProductVariantService.
 *
 * <p><b>Price model (LOCKED):</b>
 * <ul>
 *   <li>{@code price}          — required, final selling price</li>
 *   <li>{@code compareAtPrice} — optional, must be > price when provided</li>
 *   <li>{@code costPrice}      — optional, internal COGS (admin only)</li>
 * </ul>
 */
@Data
public class CreateVariantRequest {

    @NotNull(message = "Color attribute value is required")
    private Long colorAttributeValueId;

    @NotNull(message = "Size attribute value is required")
    private Long sizeAttributeValueId;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer and 2 decimal digits")
    private BigDecimal price;

    /** Optional — must be strictly greater than price when provided. */
    @DecimalMin(value = "0.01", message = "Compare-at price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Compare-at price format invalid")
    private BigDecimal compareAtPrice;

    /** Internal cost — never exposed in public API. */
    @DecimalMin(value = "0.00", message = "Cost price must not be negative")
    @Digits(integer = 8, fraction = 2, message = "Cost price format invalid")
    private BigDecimal costPrice;

    @Min(value = 0, message = "Initial stock must be 0 or more")
    private Integer initialStock = 0;

    @Min(value = 1, message = "Low stock threshold must be at least 1")
    private Integer lowStockThreshold = 5;

    @Min(value = 1, message = "Weight must be at least 1 gram")
    private Integer weightGrams;
}
