package com.ego.raw_ego.coupon.dto.request;

import com.ego.raw_ego.coupon.enums.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for {@code POST /api/v1/admin/coupons}.
 */
@Getter
@NoArgsConstructor
public class CreateCouponRequest {

    @NotBlank(message = "Coupon code is required.")
    @Size(min = 3, max = 50, message = "Coupon code must be between 3 and 50 characters.")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$",
             message = "Coupon code may only contain letters, numbers, hyphens, and underscores.")
    private String code;

    @Size(max = 255, message = "Description must not exceed 255 characters.")
    private String description;

    @NotNull(message = "Discount type is required.")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required.")
    @DecimalMin(value = "0.01", message = "Discount value must be greater than 0.")
    private BigDecimal discountValue;

    /** For PERCENTAGE type: maximum rupee discount. Null = no cap. */
    @DecimalMin(value = "0.01", message = "Max discount amount must be greater than 0.")
    private BigDecimal maxDiscountAmount;

    /** Minimum subtotal required. Null = no minimum. */
    @DecimalMin(value = "0.01", message = "Min order amount must be greater than 0.")
    private BigDecimal minOrderAmount;

    /** Maximum total uses. Null = unlimited. */
    @Min(value = 1, message = "Max uses must be at least 1.")
    private Integer maxUses;

    /** Expiry timestamp in ISO-8601. Null = never expires. */
    private Instant expiresAt;
}
