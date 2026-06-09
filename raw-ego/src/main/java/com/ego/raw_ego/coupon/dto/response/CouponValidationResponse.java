package com.ego.raw_ego.coupon.dto.response;

import com.ego.raw_ego.coupon.enums.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Response for the public coupon validation endpoint
 * {@code GET /api/v1/coupons/validate?code=EGO20&subtotal=1299}.
 *
 * <p>Allows the frontend to preview the discount before the user submits checkout.
 * No side effects — does NOT increment {@code currentUses}.
 */
@Getter
@Builder
public class CouponValidationResponse {

    /** Whether the coupon is valid for the given subtotal. */
    private boolean valid;

    /** Human-readable reason if invalid. Null if valid. */
    private String reason;

    /** Coupon code as stored (normalized to UPPER). */
    private String code;

    /** Discount type (FLAT or PERCENTAGE). */
    private DiscountType discountType;

    /** Raw discount value (e.g. 200.00 for FLAT, 20.00 for 20% PERCENTAGE). */
    private BigDecimal discountValue;

    /**
     * Estimated rupee discount that would be applied to the given subtotal.
     * Null if the coupon is invalid.
     */
    private BigDecimal estimatedDiscount;

    /**
     * Subtotal after discount.
     * Null if the coupon is invalid.
     */
    private BigDecimal estimatedTotal;
}
