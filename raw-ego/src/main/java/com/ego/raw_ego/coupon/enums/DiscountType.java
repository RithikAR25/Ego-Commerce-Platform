package com.ego.raw_ego.coupon.enums;

/**
 * Discount calculation type for a coupon.
 *
 * <ul>
 *   <li>{@link #FLAT} — fixed amount deducted from the order subtotal (e.g. ₹200 off).</li>
 *   <li>{@link #PERCENTAGE} — percentage of the subtotal deducted,
 *       optionally capped by {@code maxDiscountAmount} on the {@link com.ego.raw_ego.coupon.entity.Coupon}.</li>
 * </ul>
 */
public enum DiscountType {
    FLAT,
    PERCENTAGE
}
