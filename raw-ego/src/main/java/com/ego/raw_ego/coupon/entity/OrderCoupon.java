package com.ego.raw_ego.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity mapping to the {@code order_coupons} table.
 *
 * <p>Audit record linking a {@link Coupon} to an order, capturing the discount
 * as actually applied at checkout time. This is an immutable snapshot —
 * even if the coupon is later deleted or modified, this record is preserved.
 *
 * <p>One row per order (enforced by {@code UNIQUE(order_id)}).
 */
@Entity
@Table(
        name = "order_coupons",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_order_coupon_order", columnNames = {"order_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The order this coupon was applied to.
     * Raw Long FK — avoids coupling the coupon module to the order entity.
     */
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    /**
     * The coupon that was applied.
     * FK preserved for referential integrity. RESTRICT prevents coupon deletion
     * while orders reference it (admin deactivates, never hard-deletes).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    /** Snapshot of the coupon code at apply time — survives coupon code changes. */
    @Column(name = "coupon_code", nullable = false, length = 50)
    private String couponCode;

    /** Actual rupee discount applied to this order. */
    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
