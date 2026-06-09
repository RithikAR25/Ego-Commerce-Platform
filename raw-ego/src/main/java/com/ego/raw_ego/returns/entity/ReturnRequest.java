package com.ego.raw_ego.returns.entity;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.order.entity.Order;
import com.ego.raw_ego.returns.enums.ReturnReason;
import com.ego.raw_ego.returns.enums.ReturnStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity mapping to the {@code return_requests} table.
 *
 * <p>Represents a customer's return request for a delivered order. Key design decisions:
 * <ul>
 *   <li>Only one non-rejected return per order is allowed (enforced at service level).</li>
 *   <li>{@code refundAmount} is set by the admin at approval time — allows partial refunds.</li>
 *   <li>{@code razorpayRefundId} is populated after the Razorpay refund API call succeeds.</li>
 *   <li>{@code @Version} provides optimistic locking to prevent concurrent review races.</li>
 *   <li>Both {@code order} and {@code requestedBy} use LAZY loading — ownership checks
 *       need only their IDs, not the full graph.</li>
 * </ul>
 */
@Entity
@Table(
        name = "return_requests",
        indexes = {
                @Index(name = "idx_return_requests_order_id", columnList = "order_id"),
                @Index(name = "idx_return_requests_status",   columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The order being returned.
     * LAZY — only order.id and order.status are typically needed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * The customer who submitted this return request.
     * LAZY — only user.id is needed for ownership checks.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    /** Customer-selected reason category for the return. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnReason reason;

    /**
     * Optional free-text elaboration on the return reason.
     * Especially useful when reason = OTHER.
     */
    @Column(name = "reason_detail", columnDefinition = "TEXT")
    private String reasonDetail;

    /** Current state of the return request in the state machine. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReturnStatus status = ReturnStatus.REQUESTED;

    /**
     * Rupee refund amount set by admin at approval time.
     * Null until admin approves. Supports partial refunds.
     * grandTotal is the maximum permissible value (enforced at service level).
     */
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    /**
     * Razorpay refund ID returned by {@code payments.refund()} API.
     * Format: {@code rfnd_XXXXXXXXXXXXXXXXXX}.
     * Null until the Razorpay refund call completes successfully.
     */
    @Column(name = "razorpay_refund_id", length = 100)
    private String razorpayRefundId;

    /**
     * Optional notes added by the admin during review.
     * Visible to the customer on GET return status.
     */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    /** Optimistic lock — prevents concurrent admin review races. */
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
}
