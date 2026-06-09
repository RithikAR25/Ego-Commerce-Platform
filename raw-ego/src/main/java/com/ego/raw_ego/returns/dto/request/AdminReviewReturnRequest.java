package com.ego.raw_ego.returns.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request payload for {@code PUT /api/v1/admin/returns/{returnId}/review}.
 *
 * <p>Admin uses this to approve or reject a return request.
 * When {@code approve = true}, {@code refundAmount} is mandatory and must
 * be greater than zero. The service validates that {@code refundAmount}
 * does not exceed the original {@code order.grandTotal}.
 *
 * <p>When {@code approve = false}, {@code refundAmount} is ignored.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminReviewReturnRequest {

    /**
     * {@code true} to approve the return and trigger a Razorpay refund.
     * {@code false} to reject the return (terminal — no refund issued).
     */
    @NotNull(message = "Approve/reject decision is required.")
    private Boolean approve;

    /**
     * Rupee amount to refund. Required when {@code approve = true}.
     * Must be greater than 0. Maximum is the order grand total (enforced at service level).
     * Ignored when {@code approve = false}.
     */
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0.")
    private BigDecimal refundAmount;

    /**
     * Optional admin notes — visible to the customer in the return status response.
     * Examples: "Refund approved — please ship item back within 3 days.",
     *           "Rejected — item shows signs of wear beyond normal use."
     */
    @Size(max = 1000, message = "Admin notes must not exceed 1000 characters.")
    private String adminNotes;
}
