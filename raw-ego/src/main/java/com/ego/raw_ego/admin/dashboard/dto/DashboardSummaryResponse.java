package com.ego.raw_ego.admin.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Top-level response DTO for the admin dashboard KPI endpoint.
 *
 * <pre>
 * GET /api/v1/admin/dashboard/summary
 * </pre>
 *
 * <p>All numeric aggregations are computed via targeted DB queries in
 * {@link com.ego.raw_ego.admin.dashboard.service.DashboardService} — no
 * in-memory {@code findAll()} processing.
 *
 * <p>Field descriptions:
 * <ul>
 *   <li>{@code totalRevenue}    — SUM(grand_total) for CONFIRMED+ orders</li>
 *   <li>{@code totalOrders}     — COUNT(*) across all orders</li>
 *   <li>{@code pendingOrders}   — COUNT for PENDING_PAYMENT | CONFIRMED | PROCESSING</li>
 *   <li>{@code deliveredOrders} — COUNT for DELIVERED status</li>
 *   <li>{@code refundCount}     — COUNT of return_requests in REQUESTED status</li>
 *   <li>{@code lowStockCount}   — COUNT of inventory rows where qty ≤ threshold and qty > 0</li>
 *   <li>{@code recentOrders}    — last 10 orders (lightweight projection)</li>
 *   <li>{@code ordersByStatus}  — per-status order counts for charts</li>
 *   <li>{@code revenueTrend}    — 7 daily revenue totals (oldest → newest)</li>
 * </ul>
 */
@Getter
@Builder
public class DashboardSummaryResponse {

    /** Sum of grand_total for all CONFIRMED, PROCESSING, SHIPPED, DELIVERED, REFUNDED orders. */
    private final BigDecimal totalRevenue;

    /** Total number of orders across all statuses. */
    private final long totalOrders;

    /** Count of orders in PENDING_PAYMENT, CONFIRMED, or PROCESSING — active backlog. */
    private final long pendingOrders;

    /** Count of orders in DELIVERED status — successfully fulfilled. */
    private final long deliveredOrders;

    /**
     * Count of return_requests currently in REQUESTED status — pending admin review.
     * Maps to the "Pending Returns" KPI card.
     */
    private final long refundCount;

    /**
     * Count of inventory_record rows where quantity_available ≤ low_stock_threshold
     * and quantity_available > 0 (i.e., low stock, not yet out of stock).
     */
    private final long lowStockCount;

    /**
     * Last 10 orders by creation time — lightweight projections, no item/history loading.
     * Used by the "Recent Orders" panel on the dashboard.
     */
    private final List<RecentOrderRow> recentOrders;

    /**
     * Per-status order counts keyed by {@code OrderStatus.name()}.
     * Example: {@code {"PENDING_PAYMENT": 4, "CONFIRMED": 12, "DELIVERED": 88}}.
     * Used for the "Orders by Status" chart.
     */
    private final Map<String, Long> ordersByStatus;

    /**
     * 7-element list of daily revenue totals, ordered oldest → newest (today is index 6).
     * Each value is the SUM(grand_total) for CONFIRMED+ orders placed on that calendar day.
     * Used for the "Revenue Trend" line chart.
     */
    private final List<BigDecimal> revenueTrend;
}
