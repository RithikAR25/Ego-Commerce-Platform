package com.ego.raw_ego.coupon.service;

import com.ego.raw_ego.coupon.entity.Coupon;

import java.math.BigDecimal;

/**
 * Carries the validated coupon entity and the computed discount amount back
 * to the caller of {@link CouponService#validateAndComputeDiscount}.
 *
 * <p>Extracted as a top-level record so that both the {@link CouponService} interface
 * and its consumers (e.g. {@code OrderService}) can reference it without a dependency
 * on the concrete {@code CouponServiceImpl} class.
 */
public record CouponApplicationResult(Coupon coupon, BigDecimal discountAmount) {}
