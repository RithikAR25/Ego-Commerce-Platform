package com.ego.raw_ego.coupon.service;

import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.coupon.dto.request.CreateCouponRequest;
import com.ego.raw_ego.coupon.dto.request.UpdateCouponRequest;
import com.ego.raw_ego.coupon.dto.response.CouponResponse;
import com.ego.raw_ego.coupon.dto.response.CouponValidationResponse;
import com.ego.raw_ego.coupon.entity.Coupon;
import com.ego.raw_ego.coupon.entity.OrderCoupon;
import com.ego.raw_ego.coupon.enums.DiscountType;
import com.ego.raw_ego.coupon.repository.CouponRepository;
import com.ego.raw_ego.coupon.repository.OrderCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Business logic for the coupon module.
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
 * <p>{@link #validateAndComputeDiscount} is called by {@link com.ego.raw_ego.order.service.OrderService}
 * inside the checkout {@code @Transactional} boundary. The coupon entity is returned
 * so OrderService can persist {@link OrderCoupon} and call {@link #recordUsage} atomically.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository      couponRepository;
    private final OrderCouponRepository orderCouponRepository;

    // ── Validation (public, no side effects) ──────────────────────────────────

    /**
     * Public validation endpoint — previews the discount without applying it.
     * No side effects on currentUses.
     *
     * @param code     coupon code (case-insensitive)
     * @param subtotal the cart subtotal to compute discount against
     */
    @Transactional(readOnly = true)
    public CouponValidationResponse validatePreview(String code, BigDecimal subtotal) {
        String normalized = code.trim().toUpperCase();
        Coupon coupon = couponRepository.findByCodeAndActiveTrue(normalized).orElse(null);

        if (coupon == null) {
            return invalid("Coupon code \"" + code + "\" is invalid or has expired.", null);
        }

        String reason = validateCouponConstraints(coupon, subtotal);
        if (reason != null) {
            return invalid(reason, coupon);
        }

        BigDecimal discount = computeDiscount(coupon, subtotal);
        BigDecimal estimatedTotal = subtotal.subtract(discount).max(BigDecimal.ZERO);

        return CouponValidationResponse.builder()
                .valid(true)
                .reason(null)
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .estimatedDiscount(discount)
                .estimatedTotal(estimatedTotal)
                .build();
    }

    /**
     * Called from within the checkout transaction — validates the coupon and returns
     * the computed discount amount. Throws {@link ConflictException} if invalid.
     *
     * <p>Does NOT increment {@code currentUses} — call {@link #recordUsage} separately
     * after the order is persisted, still within the same transaction.
     *
     * @param code     coupon code (case-insensitive)
     * @param subtotal the order subtotal
     * @return [coupon, discountAmount] — both needed by OrderService to persist OrderCoupon
     * @throws ConflictException if the coupon is invalid for any reason
     */
    @Transactional
    public CouponApplicationResult validateAndComputeDiscount(String code, BigDecimal subtotal) {
        String normalized = code.trim().toUpperCase();
        Coupon coupon = couponRepository.findByCodeAndActiveTrue(normalized)
                .orElseThrow(() -> new ConflictException(
                        "Coupon code \"" + code + "\" is invalid or has expired."));

        String reason = validateCouponConstraints(coupon, subtotal);
        if (reason != null) {
            throw new ConflictException(reason);
        }

        BigDecimal discount = computeDiscount(coupon, subtotal);
        log.info("Coupon validated: code={} discount={}", normalized, discount);
        return new CouponApplicationResult(coupon, discount);
    }

    /**
     * Persists the audit record and atomically increments currentUses.
     * Must be called within the same {@code @Transactional} context as checkout.
     */
    @Transactional
    public void recordUsage(Coupon coupon, Long orderId, BigDecimal discountAmount) {
        OrderCoupon orderCoupon = OrderCoupon.builder()
                .orderId(orderId)
                .coupon(coupon)
                .couponCode(coupon.getCode())
                .discountAmount(discountAmount)
                .build();
        orderCouponRepository.save(orderCoupon);
        couponRepository.incrementUses(coupon.getId());

        log.info("Coupon usage recorded: code={} orderId={} discount={}",
                coupon.getCode(), orderId, discountAmount);
    }

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (couponRepository.existsByCode(code)) {
            throw new ConflictException("Coupon code \"" + code + "\" already exists.");
        }

        Coupon coupon = Coupon.builder()
                .code(code)
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minOrderAmount(request.getMinOrderAmount())
                .maxUses(request.getMaxUses())
                .expiresAt(request.getExpiresAt())
                .build();

        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon created: id={} code={} type={}", saved.getId(), saved.getCode(), saved.getDiscountType());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> adminListCoupons(Pageable pageable) {
        return couponRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CouponResponse adminGetCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: id=" + id));
        return toResponse(coupon);
    }

    @Transactional
    public CouponResponse updateCoupon(Long id, UpdateCouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: id=" + id));

        if (request.getDescription()       != null) coupon.setDescription(request.getDescription());
        if (request.getDiscountType()      != null) coupon.setDiscountType(request.getDiscountType());
        if (request.getDiscountValue()     != null) coupon.setDiscountValue(request.getDiscountValue());
        if (request.getMaxDiscountAmount() != null) coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
        if (request.getMinOrderAmount()    != null) coupon.setMinOrderAmount(request.getMinOrderAmount());
        if (request.getMaxUses()           != null) coupon.setMaxUses(request.getMaxUses());
        if (request.getExpiresAt()         != null) coupon.setExpiresAt(request.getExpiresAt());
        if (request.getActive()            != null) coupon.setActive(request.getActive());

        return toResponse(couponRepository.save(coupon));
    }

    /**
     * Soft-delete: sets {@code active = false}. Never hard-deletes because
     * {@code order_coupons} rows reference this coupon (RESTRICT FK).
     */
    @Transactional
    public void deactivateCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: id=" + id));
        coupon.setActive(false);
        couponRepository.save(coupon);
        log.info("Coupon deactivated (soft-delete): id={} code={}", id, coupon.getCode());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Validates coupon constraints against the given subtotal.
     * Returns null if valid, or a user-friendly error message if not.
     */
    private String validateCouponConstraints(Coupon coupon, BigDecimal subtotal) {
        // Expiry check
        if (coupon.getExpiresAt() != null && Instant.now().isAfter(coupon.getExpiresAt())) {
            return "Coupon \"" + coupon.getCode() + "\" has expired.";
        }
        // Max uses check
        if (coupon.getMaxUses() != null && coupon.getCurrentUses() >= coupon.getMaxUses()) {
            return "Coupon \"" + coupon.getCode() + "\" has reached its usage limit.";
        }
        // Minimum order amount check
        if (coupon.getMinOrderAmount() != null
                && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            return "A minimum order of ₹" + coupon.getMinOrderAmount().toPlainString() +
                   " is required to use coupon \"" + coupon.getCode() + "\".";
        }
        return null; // all checks passed
    }

    /**
     * Computes the actual rupee discount for the given coupon and subtotal.
     * Result is always >= 0 and <= subtotal.
     */
    private BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.FLAT) {
            discount = coupon.getDiscountValue();
        } else {
            // PERCENTAGE
            discount = subtotal
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            // Cap by maxDiscountAmount if configured
            if (coupon.getMaxDiscountAmount() != null) {
                discount = discount.min(coupon.getMaxDiscountAmount());
            }
        }
        // Discount can never exceed subtotal (no negative grand totals)
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private CouponValidationResponse invalid(String reason, Coupon coupon) {
        return CouponValidationResponse.builder()
                .valid(false)
                .reason(reason)
                .code(coupon != null ? coupon.getCode() : null)
                .discountType(coupon != null ? coupon.getDiscountType() : null)
                .discountValue(coupon != null ? coupon.getDiscountValue() : null)
                .estimatedDiscount(null)
                .estimatedTotal(null)
                .build();
    }

    private CouponResponse toResponse(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxUses(coupon.getMaxUses())
                .currentUses(coupon.getCurrentUses())
                .active(coupon.isActive())
                .expiresAt(coupon.getExpiresAt())
                .createdAt(coupon.getCreatedAt())
                .updatedAt(coupon.getUpdatedAt())
                .build();
    }

    // ── Inner record for passing result from validateAndComputeDiscount ───────

    /**
     * Carries the coupon entity and computed discount back to OrderService
     * so it can persist OrderCoupon and call recordUsage in one transaction.
     */
    public record CouponApplicationResult(Coupon coupon, BigDecimal discountAmount) {}
}
