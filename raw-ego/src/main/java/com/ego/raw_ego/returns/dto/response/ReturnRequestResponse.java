package com.ego.raw_ego.returns.dto.response;

import com.ego.raw_ego.returns.enums.ReturnReason;
import com.ego.raw_ego.returns.enums.ReturnStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for all return request endpoints.
 *
 * <p>Returned by:
 * <ul>
 *   <li>{@code POST /api/v1/orders/{orderId}/returns} — after customer initiates</li>
 *   <li>{@code GET /api/v1/orders/{orderId}/returns} — customer status check</li>
 *   <li>{@code GET /api/v1/admin/returns/{returnId}} — admin detail view</li>
 *   <li>{@code PUT /api/v1/admin/returns/{returnId}/review} — after admin action</li>
 * </ul>
 */
@Getter
@Builder
public class ReturnRequestResponse {

    /** Unique return request ID. */
    private Long id;

    /** EGO order ID this return belongs to. */
    private Long orderId;

    /** ID of the customer who submitted the return. */
    private Long requestedById;

    /** Current status in the return state machine. */
    private ReturnStatus status;

    /** Reason category selected by the customer. */
    private ReturnReason reason;

    /**
     * Optional free-text elaboration from the customer.
     * Null if not provided.
     */
    private String reasonDetail;

    /**
     * Rupee refund amount approved by admin.
     * Null until admin approves the return.
     */
    private BigDecimal refundAmount;

    /**
     * Razorpay refund ID returned by the Razorpay API.
     * Format: {@code rfnd_XXXXXXXXXXXXXXXXXX}.
     * Null until the Razorpay refund call completes.
     */
    private String razorpayRefundId;

    /**
     * Optional admin notes visible to the customer.
     * Null if the admin did not add notes.
     */
    private String adminNotes;

    /** Return request creation timestamp. */
    private Instant createdAt;

    /** Last update timestamp (status changes update this). */
    private Instant updatedAt;
}
