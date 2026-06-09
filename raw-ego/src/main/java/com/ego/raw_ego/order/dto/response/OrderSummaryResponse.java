package com.ego.raw_ego.order.dto.response;

import com.ego.raw_ego.order.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight order response used in paginated list views.
 * Used by: {@code GET /api/v1/orders} and {@code GET /api/v1/admin/orders}.
 *
 * <p>Contains only the fields needed for a list row:
 * ID, status, total, item count, and date. The full item breakdown
 * is available in {@link OrderDetailResponse}.
 */
@Getter
@Builder
public class OrderSummaryResponse {

    private Long id;

    /** Current order status. */
    private OrderStatus status;

    /** Final charged amount (subtotal + shippingTotal). */
    private BigDecimal grandTotal;

    /** Total units across all line items — displayed in order history UI. */
    private int itemCount;

    /** Timestamp when the order was created. */
    private Instant createdAt;
}
