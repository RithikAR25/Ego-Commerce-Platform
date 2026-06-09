package com.ego.raw_ego.coupon.entity;

import com.ego.raw_ego.coupon.enums.DiscountType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity mapping to the {@code coupons} table.
 *
 * <p><b>Discount types:</b>
 * <ul>
 *   <li>{@link DiscountType#FLAT} — fixed rupee amount deducted from subtotal.</li>
 *   <li>{@link DiscountType#PERCENTAGE} — percentage of subtotal, capped by
 *       {@link #maxDiscountAmount} if set.</li>
 * </ul>
 *
 * <p><b>Constraints:</b>
 * <ul>
 *   <li>{@link #minOrderAmount} (nullable) — minimum subtotal required to apply coupon.</li>
 *   <li>{@link #maxUses} (nullable) — unlimited if null.</li>
 *   <li>{@link #expiresAt} (nullable) — never expires if null.</li>
 * </ul>
 *
 * <p><b>Soft delete:</b> Coupons are deactivated ({@link #active} = false), never hard-deleted.
 * This preserves the audit trail in {@code order_coupons}.
 *
 * <p><b>Concurrency:</b> {@link #currentUses} is incremented inside the checkout
 * transaction using a {@code @Modifying} query — safe for concurrent checkouts.
 */
@Entity
@Table(
        name = "coupons",
        indexes = {
                @Index(name = "idx_coupon_code", columnList = "code", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique coupon code, case-insensitive at service layer (stored UPPER). */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Human-readable description for admin UI (e.g. "Summer sale — 20% off"). */
    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /**
     * For FLAT: the rupee amount off. For PERCENTAGE: the percentage value (e.g. 20.00 = 20%).
     */
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /**
     * For PERCENTAGE type: maximum rupee discount allowed.
     * Example: 20% off, capped at ₹500.
     * Null = no cap.
     */
    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    /**
     * Minimum order subtotal required to use this coupon.
     * Null = no minimum.
     */
    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    /**
     * Maximum total number of times this coupon can be used.
     * Null = unlimited uses.
     */
    @Column(name = "max_uses")
    private Integer maxUses;

    /**
     * How many times this coupon has been successfully applied.
     * Incremented atomically inside the checkout transaction.
     */
    @Column(name = "current_uses", nullable = false)
    @Builder.Default
    private int currentUses = 0;

    /**
     * Whether this coupon is active. Inactive coupons are rejected at validation.
     * Admin uses this for soft-delete / deactivation.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Expiry timestamp. Null = never expires.
     * Coupons are rejected if {@code Instant.now().isAfter(expiresAt)}.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
