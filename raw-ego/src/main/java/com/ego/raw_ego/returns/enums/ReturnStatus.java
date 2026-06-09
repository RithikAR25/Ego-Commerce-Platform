package com.ego.raw_ego.returns.enums;

/**
 * Return request lifecycle state machine.
 *
 * <pre>
 *   REQUESTED
 *       │
 *       ├──(admin approves)──► APPROVED ──► REFUND_INITIATED ──► REFUND_COMPLETED
 *       │
 *       └──(admin rejects)──► REJECTED  (terminal)
 * </pre>
 *
 * <p><b>Terminal states:</b> REJECTED, REFUND_COMPLETED — no further transitions.
 *
 * <p>In practice, APPROVED → REFUND_INITIATED → REFUND_COMPLETED transitions happen
 * atomically inside {@code ReturnService.adminReviewReturn()} when the admin approves.
 * APPROVED is a transient intermediate state stored only briefly before the Razorpay
 * refund call completes.
 */
public enum ReturnStatus {

    /** Customer submitted the return request; awaiting admin review. */
    REQUESTED,

    /**
     * Admin approved the return; Razorpay refund is about to be initiated.
     * Transient — set just before the Razorpay API call.
     */
    APPROVED,

    /** Razorpay refund API called successfully; refund is in processing. */
    REFUND_INITIATED,

    /** Razorpay confirmed the refund. Order transitions to REFUNDED. Terminal. */
    REFUND_COMPLETED,

    /** Admin rejected the return request. Terminal — no further action. */
    REJECTED;

    /**
     * Validates whether an admin-driven status transition is legal.
     * Throws {@link IllegalArgumentException} on invalid transitions.
     *
     * <p>Customer-initiated state: only REQUESTED → admin review.
     * All subsequent transitions are admin-driven (approve / reject).
     *
     * @param current current return status
     * @param next    target status requested by admin
     */
    public static void assertValidAdminTransition(ReturnStatus current, ReturnStatus next) {
        boolean valid = switch (current) {
            case REQUESTED         -> next == APPROVED || next == REJECTED;
            case APPROVED          -> next == REFUND_INITIATED;
            case REFUND_INITIATED  -> next == REFUND_COMPLETED;
            // Terminal states — no transitions allowed
            default                -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid return status transition: " + current + " → " + next +
                    ". Allowed: REQUESTED→APPROVED|REJECTED, APPROVED→REFUND_INITIATED, " +
                    "REFUND_INITIATED→REFUND_COMPLETED.");
        }
    }
}
