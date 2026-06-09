package com.ego.raw_ego.order.dto.response;

import com.ego.raw_ego.order.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Response DTO for a single entry in an order's status history audit trail.
 * Used inside {@link OrderDetailResponse#getStatusHistory()}.
 */
@Getter
@Builder
public class OrderStatusHistoryResponse {

    /** The status recorded at this point in the lifecycle. */
    private OrderStatus status;

    /** Optional admin or system note attached to this transition. Null if not provided. */
    private String note;

    /** Timestamp when this status entry was recorded. */
    private Instant createdAt;
}
