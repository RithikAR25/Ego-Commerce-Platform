package com.ego.raw_ego.order.entity;

import com.ego.raw_ego.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity mapping to the {@code order_status_history} table.
 *
 * <p>Append-only audit trail. Records are never updated or deleted.
 * One entry is created at order placement (PENDING_PAYMENT), and one
 * is appended for each subsequent status transition.
 *
 * <p>Set via {@link Order#addStatusHistory(OrderStatusHistory)}.
 */
@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent order. LAZY — accessed via the Order aggregate only.
     * Set via {@link Order#addStatusHistory(OrderStatusHistory)}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** The status value at this point in the lifecycle. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    /**
     * Optional free-text note from admin (e.g. "Payment received via cash")
     * or system (e.g. "Payment timeout"). Null for automatic transitions.
     */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** Timestamp of the status transition. Set by Hibernate — never manually assigned. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
