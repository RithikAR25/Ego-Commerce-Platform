package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Request body for updating variant pricing and active status (admin only). */
@Data
public class UpdateVariantRequest {

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price format invalid")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Compare-at price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Compare-at price format invalid")
    private BigDecimal compareAtPrice;

    @DecimalMin(value = "0.00", message = "Cost price must not be negative")
    @Digits(integer = 8, fraction = 2, message = "Cost price format invalid")
    private BigDecimal costPrice;

    @Min(value = 1, message = "Weight must be at least 1 gram")
    private Integer weightGrams;

    private Boolean active;
}
