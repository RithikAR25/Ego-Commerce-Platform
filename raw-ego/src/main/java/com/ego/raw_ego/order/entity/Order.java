package com.ego.raw_ego.order.entity;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity mapping to the {@code orders} table.
 *
 * <p>Represents a single customer order. Key design decisions:
 * <ul>
 *   <li>{@code shippingAddress} is kept for legacy test data. New orders use
 *       {@code addressSnapshot} (structured JSON snapshot captured at checkout).
 *       Never a FK — addresses may change, orders must not.</li>
 *   <li>{@code @Version} provides optimistic locking for concurrent status updates.</li>
 *   <li>{@code OrderItem} and {@code OrderStatusHistory} children are cascade-persisted
 *       with the parent — they are created together inside {@code OrderService.checkout()}.</li>
 *   <li>The {@code user} FK uses {@code LAZY} loading — most queries need only
 *       {@code user.id} for the ownership guard, not the full user graph.</li>
 * </ul>
 */
@Entity
@Table(
        name = "orders",
        indexes = {
                // Webhook handler lookups: findByRazorpayOrderId() — needs to be fast at scale
                @Index(name = "idx_orders_razorpay_order_id", columnList = "razorpay_order_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The customer who placed this order.
     * LAZY load — only user.id is typically needed for ownership checks.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /** Sum of all line totals (unit_price_snapshot × quantity). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /** Flat shipping fee. Defaults to 0.00 until Phase 9 shipping calculator. */
    @Column(name = "shipping_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingTotal = BigDecimal.ZERO;

    /** subtotal + shippingTotal. Pre-computed at checkout — never recalculated. */
    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    /**
     * Full shipping address captured as a plain text snapshot at checkout time.
     * Kept for legacy test data. New orders populate {@code addressSnapshot} instead.
     */
    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    /**
     * JSON snapshot of the {@link com.ego.raw_ego.address.entity.UserAddress} selected at checkout.
     * Format: compact JSON object containing all address fields at the moment of purchase.
     * Immutable — never updated after order creation.
     *
     * <p>Example:
     * <pre>
     * {"fullName":"Rithik A","phone":"9876543210","addressLine1":"12 MG Road",
     *  "city":"Bengaluru","state":"Karnataka","pinCode":"560001","country":"India"}
     * </pre>
     */
    @Column(name = "address_snapshot", columnDefinition = "TEXT")
    private String addressSnapshot;

    /**
     * Coupon code used on this order — snapshot captured at checkout time (Phase 9).
     * Null if no coupon was applied. Preserved even if the coupon is later deactivated.
     */
    @Column(name = "coupon_code_snapshot", length = 50)
    private String couponCodeSnapshot;

    /**
     * Rupee discount applied via coupon at checkout time (Phase 9).
     * Zero (not null) if no coupon was applied.
     * grandTotal = subtotal - discountAmount + shippingTotal.
     */
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /**
     * Razorpay order ID assigned when {@code POST /api/v1/payments/razorpay/create} is called.
     * Format: {@code order_XXXXXXXXXXXXXXXXXX}.
     * Null until the customer initiates payment.
     * Unique per EGO order — used as idempotency key in the webhook handler.
     */
    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    /**
     * Razorpay payment ID captured from the webhook {@code payment.captured} event.
     * Format: {@code pay_XXXXXXXXXXXXXXXXXX}.
     * Null until Razorpay confirms payment capture.
     * Set atomically with the status transition to {@code CONFIRMED}.
     */
    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    // ── Shipment tracking fields ───────────────────────────────────────────────

    /**
     * Courier tracking number set when the order is advanced to SHIPPED or OUT_FOR_DELIVERY.
     * Example: "DTDC123456789IN", "DELHIVERY9876543210".
     * Null until admin provides it on shipment update.
     */
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    /**
     * Courier/logistics partner name (e.g. "Delhivery", "DTDC", "BlueDart", "Ekart").
     * Null until admin provides it on shipment update.
     */
    @Column(name = "courier_name", length = 100)
    private String courierName;

    /**
     * Deep link to the courier's customer-facing tracking page.
     * Example: "https://www.delhivery.com/track/package/DELHIVERY9876543210".
     * Null until admin provides it. Length 500 to accommodate long tracking URLs.
     */
    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    /**
     * Estimated delivery date communicated to the customer at shipment time.
     * Null until admin provides it when advancing to SHIPPED or OUT_FOR_DELIVERY.
     */
    @Column(name = "estimated_delivery_at")
    private java.time.Instant estimatedDeliveryAt;

    /** Optimistic lock — prevents concurrent status overwrite races. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Relationships ─────────────────────────────────────────────────────────

    /**
     * Line items belonging to this order.
     * CascadeType.ALL + orphanRemoval ensures items are persisted and deleted with the order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /**
     * Audit trail of status changes.
     * Append-only — records are never updated or deleted.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    // ── Convenience helpers ───────────────────────────────────────────────────

    /** Wires both sides of the relationship before persisting. */
    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Appends a status history entry, wiring both sides of the relationship. */
    public void addStatusHistory(OrderStatusHistory entry) {
        entry.setOrder(this);
        statusHistory.add(entry);
    }
}
