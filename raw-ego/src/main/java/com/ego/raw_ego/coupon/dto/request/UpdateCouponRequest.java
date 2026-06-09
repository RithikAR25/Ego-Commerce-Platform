package com.ego.raw_ego.coupon.dto.request;

import com.ego.raw_ego.coupon.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for {@code PUT /api/v1/admin/coupons/{id}}.
 * All fields are optional — only non-null fields are applied.
 */
@Getter
@NoArgsConstructor
public class UpdateCouponRequest {

    @Size(max = 255, message = "Description must not exceed 255 characters.")
    private String description;

    private DiscountType discountType;

    @DecimalMin(value = "0.01", message = "Discount value must be greater than 0.")
    private BigDecimal discountValue;

    @DecimalMin(value = "0.01", message = "Max discount amount must be greater than 0.")
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0.01", message = "Min order amount must be greater than 0.")
    private BigDecimal minOrderAmount;

    @Min(value = 1, message = "Max uses must be at least 1.")
    private Integer maxUses;

    private Instant expiresAt;

    private Boolean active;
}
