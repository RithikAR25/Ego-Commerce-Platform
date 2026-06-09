package com.ego.raw_ego.coupon.dto.response;

import com.ego.raw_ego.coupon.enums.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Coupon detail response — used in admin management and validate endpoint.
 */
@Getter
@Builder
public class CouponResponse {

    private Long id;
    private String code;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private Integer maxUses;
    private int currentUses;
    private boolean active;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
