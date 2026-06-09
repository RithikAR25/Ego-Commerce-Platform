package com.ego.raw_ego.admin.dashboard.repository;

import com.ego.raw_ego.admin.dashboard.dto.RecentOrderRow;
import com.ego.raw_ego.order.entity.Order;
import com.ego.raw_ego.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard-scoped aggregate read queries on the {@code orders} table.
 *
 * <p>Extends Spring's marker {@link Repository} (not {@link org.springframework.data.jpa.repository.JpaRepository})
 * so only the explicitly declared aggregate methods are exposed — no accidental
 * {@code findAll()} or {@code deleteAll()} surface.
 *
 * <p>All methods are {@code @Query}-backed JPQL — no derived method name parsing.
 * Query semantics:
 * <ul>
 *   <li>{@link #sumGrandTotalByStatusIn} — single SQL SUM aggregate, no entity loading</li>
 *   <li>{@link #countAllOrders} — single COUNT aggregate</li>
 *   <li>{@link #countByStatusIn} — COUNT with IN clause filter</li>
 *   <li>{@link #countGroupedByStatus} — GROUP BY aggregate returning raw {@code Object[]}</li>
 *   <li>{@link #findRecentOrders} — JPQL constructor expression — only 4 scalar columns loaded</li>
 *   <li>{@link #revenueForDay} — native SQL for DATE() grouping</li>
 * </ul>
 */
@org.springframework.stereotype.Repository
public interface DashboardRepository extends Repository<Order, Long> {

    /**
     * SUM of grand_total for all orders whose status is in the provided list.
     * Returns {@code null} (not zero) when no matching rows exist — callers must handle null.
     *
     * <p>Used to compute {@code totalRevenue} (CONFIRMED, PROCESSING, SHIPPED, DELIVERED, REFUNDED).
     */
    @Query("SELECT SUM(o.grandTotal) FROM Order o WHERE o.status IN :statuses")
    BigDecimal sumGrandTotalByStatusIn(@Param("statuses") List<OrderStatus> statuses);

    /**
     * Total order count across all statuses.
     * Used for the "Total Orders" KPI card.
     */
    @Query("SELECT COUNT(o) FROM Order o")
    long countAllOrders();

    /**
     * Count of orders whose status is in the provided list.
     * Used for "Pending Orders" (PENDING_PAYMENT | CONFIRMED | PROCESSING)
     * and "Delivered Orders" counts.
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<OrderStatus> statuses);

    /**
     * Groups orders by status and returns a count per group.
     * Returns a list of {@code Object[2]} rows where:
     * <ul>
     *   <li>{@code row[0]} — {@link OrderStatus} enum value (Hibernate maps it correctly)</li>
     *   <li>{@code row[1]} — {@code Long} count</li>
     * </ul>
     * Used to build the {@code ordersByStatus} map for the doughnut chart.
     */
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupedByStatus();

    /**
     * Loads the last N orders as lightweight {@link RecentOrderRow} projections.
     *
     * <p>Uses a JPQL constructor expression — only {@code id}, {@code status},
     * {@code grand_total}, and {@code created_at} columns are selected.
     * The {@code items} and {@code statusHistory} lazy collections are never touched.
     *
     * <p>Callers should pass {@code PageRequest.of(0, 10)} to get the 10 most recent.
     */
    @Query("""
            SELECT new com.ego.raw_ego.admin.dashboard.dto.RecentOrderRow(
                o.id, o.status, o.grandTotal, o.createdAt)
            FROM Order o
            ORDER BY o.createdAt DESC
            """)
    Page<RecentOrderRow> findRecentOrders(Pageable pageable);

    /**
     * Computes the total revenue for a single calendar day using a native SQL DATE() call.
     *
     * <p>A native query is used because JPQL cannot group by {@code DATE(created_at)} portably.
     * The {@code :day} parameter must be a date string in {@code YYYY-MM-DD} format.
     *
     * <p>Only CONFIRMED, PROCESSING, SHIPPED, DELIVERED, and REFUNDED orders are counted
     * (PENDING_PAYMENT and CANCELLED are excluded — no revenue confirmed yet).
     *
     * <p>Returns {@code null} when no orders exist for that day — caller maps null → 0.
     */
    @Query(value = """
            SELECT SUM(grand_total)
            FROM orders
            WHERE DATE(created_at) = :day
              AND status IN ('CONFIRMED','PROCESSING','SHIPPED','DELIVERED','REFUNDED')
            """,
            nativeQuery = true)
    BigDecimal revenueForDay(@Param("day") String day);
}
