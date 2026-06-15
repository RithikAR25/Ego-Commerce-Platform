package com.ego.raw_ego.coupon.service;

import com.ego.raw_ego.coupon.dto.request.CreateCouponRequest;
import com.ego.raw_ego.coupon.dto.request.UpdateCouponRequest;
import com.ego.raw_ego.coupon.dto.response.CouponResponse;
import com.ego.raw_ego.coupon.dto.response.CouponValidationResponse;
import com.ego.raw_ego.coupon.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * Contract for coupon business logic.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>Coupon must exist and be active.</li>
 *   <li>Coupon must not be expired ({@code expiresAt} check).</li>
 *   <li>If {@code maxUses} is set, {@code currentUses < maxUses} must hold.</li>
 *   <li>If {@code minOrderAmount} is set, subtotal must be >= minOrderAmount.</li>
 * </ul>
 *
 * <h3>Discount calculation</h3>
 * <ul>
 *   <li>FLAT: {@code discount = min(discountValue, subtotal)} — cannot produce negative total.</li>
 *   <li>PERCENTAGE: {@code discount = subtotal × (discountValue/100)},
 *       capped by {@code maxDiscountAmount} if set, and capped by subtotal.</li>
 * </ul>
 *
 * <h3>Checkout integration</h3>
 * <p>{@link #validateAndComputeDiscount} is called by {@code OrderService}
 * inside the checkout {@code @Transactional} boundary. The coupon entity is returned
 * via {@link CouponApplicationResult} so OrderService can persist {@code OrderCoupon}
 * and call {@link #recordUsage} atomically.
 */
public interface CouponService {

    /**
     * Public validation endpoint — previews the discount without applying it.
     * No side effects on currentUses.
     *
     * @param code     coupon code (case-insensitive)
     * @param subtotal the cart subtotal to compute discount against
     */
    CouponValidationResponse validatePreview(String code, BigDecimal subtotal);

    /**
     * Called from within the checkout transaction — validates the coupon and returns
     * the computed discount amount. Throws {@link com.ego.raw_ego.common.exception.ConflictException} if invalid.
     *
     * <p>Does NOT increment {@code currentUses} — call {@link #recordUsage} separately
     * after the order is persisted, still within the same transaction.
     *
     * @param code     coupon code (case-insensitive)
     * @param subtotal the order subtotal
     * @return {@link CouponApplicationResult} carrying both the coupon entity and the discount amount
     */
    CouponApplicationResult validateAndComputeDiscount(String code, BigDecimal subtotal);

    /**
     * Persists the audit record and atomically increments currentUses.
     * Must be called within the same {@code @Transactional} context as checkout.
     */
    void recordUsage(Coupon coupon, Long orderId, BigDecimal discountAmount);

    /** Creates a new coupon. */
    CouponResponse createCoupon(CreateCouponRequest request);

    /** Admin: paginated list of all coupons, newest first. */
    Page<CouponResponse> adminListCoupons(Pageable pageable);

    /** Admin: fetch a single coupon by ID. */
    CouponResponse adminGetCoupon(Long id);

    /** Admin: partial update of a coupon. */
    CouponResponse updateCoupon(Long id, UpdateCouponRequest request);

    /**
     * Soft-delete: sets {@code active = false}.
     * Never hard-deletes because {@code order_coupons} rows reference this coupon (RESTRICT FK).
     */
    void deactivateCoupon(Long id);
}
