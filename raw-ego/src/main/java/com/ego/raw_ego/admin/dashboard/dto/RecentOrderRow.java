package com.ego.raw_ego.admin.dashboard.dto;

import com.ego.raw_ego.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight projection of a single order row used in the dashboard "Recent Orders" panel.
 *
 * <p>Populated via JPQL constructor expression — no lazy-proxy access, no N+1.
 * Only the four scalar columns needed for the list view are loaded.
 *
 * <p>Used by: {@link DashboardSummaryResponse#getRecentOrders()}.
 */
@Getter
@AllArgsConstructor
public class RecentOrderRow {

    /** EGO order ID. */
    private final Long id;

    /** Current order lifecycle status. */
    private final OrderStatus status;

    /** Final charged amount (subtotal − discount + shipping). */
    private final BigDecimal grandTotal;

    /** Timestamp when the order was created. */
    private final Instant createdAt;
}
