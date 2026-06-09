package com.ego.raw_ego.order.repository;

import com.ego.raw_ego.order.entity.Order;
import com.ego.raw_ego.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link Order} entities.
 *
 * <p>Ownership enforcement pattern:
 * Customer-facing queries use {@code findByIdAndUserId} to prevent
 * order ID enumeration attacks. A non-owner receives a 404 (not a 403)
 * so that order existence is not leaked.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Returns all orders for a specific user, newest first.
     * Used by: {@code GET /api/v1/orders}
     */
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Fetches an order by ID only if it belongs to the specified user.
     * Returns empty if the order doesn't exist OR belongs to another user.
     * Used by: {@code GET /api/v1/orders/{id}} and {@code POST /api/v1/orders/{id}/cancel}
     */
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    /**
     * Admin: all orders filtered by status, newest first.
     * Used by: {@code GET /api/v1/admin/orders?status=...}
     *
     * <p>When {@code status} is null, returns all orders — the {@code :status IS NULL}
     * condition short-circuits the status filter.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
            ORDER BY o.createdAt DESC
            """)
    Page<Order> findAllByStatusFilter(OrderStatus status, Pageable pageable);

    /**
     * Looks up an order by its Razorpay order ID.
     * Used by the webhook handler ({@code POST /api/v1/webhooks/razorpay}) to find
     * the EGO order corresponding to the incoming {@code payment.captured} event.
     */
    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Loads an Order with its {@code User} and {@code items} eagerly initialized
     * in a single JOIN FETCH query.
     *
     * <p><b>Required for async/detached contexts</b> — specifically for the
     * {@code NotificationService} which runs on a separate async thread
     * ({@code ego-async-*}). Standard {@code findById()} loads the Order but
     * leaves {@code User} and {@code items} as LAZY proxies. When the
     * Hibernate session closes after the repository call, those proxies become
     * uninitialized and inaccessible — causing {@link org.hibernate.LazyInitializationException}.
     *
     * <p>This query solves the problem by initializing the full object graph
     * within the repository call's own short-lived session. By the time the
     * method returns, {@code order.getUser().getEmail()} and iteration over
     * {@code order.getItems()} are safe from any thread.
     *
     * <p>Used by: {@code NotificationService.sendOrderConfirmation()} and
     * {@code NotificationService.sendPaymentConfirmation()}.
     */
    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.user
            JOIN FETCH o.items
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithUserAndItems(Long id);

    /**
     * Loads an Order with its {@code statusHistory} eagerly initialized,
     * filtered by ownership (userId).
     *
     * <p>Used by {@code ReturnService.initiateReturn()} to read the exact
     * {@code DELIVERED} status-transition timestamp from the audit trail,
     * which is required for accurate return-window enforcement.
     *
     * <p>A LEFT JOIN FETCH on statusHistory is required because the collection is
     * LAZY — it would trigger a LazyInitializationException if accessed after
     * the transactional session boundary.
     */
    @Query("""
            SELECT o FROM Order o
            LEFT JOIN FETCH o.statusHistory
            WHERE o.id = :id AND o.user.id = :userId
            """)
    Optional<Order> findByIdAndUserIdWithStatusHistory(Long id, Long userId);
}

