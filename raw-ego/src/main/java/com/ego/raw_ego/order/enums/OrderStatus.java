package com.ego.raw_ego.order.enums;

/**
 * Order lifecycle state machine.
 *
 * <pre>
 *   PENDING_PAYMENT
 *       │
 *       ├──(payment confirmed / Phase 7)──► CONFIRMED
 *       │                                        │
 *       │                                        ├──► PROCESSING
 *       │                                        │         │
 *       │                                        │         └──► SHIPPED ──► OUT_FOR_DELIVERY ──► DELIVERED
 *       │                                        │
 *       │                                        └──► CANCELLED
 *       │
 *       └──(customer cancel / payment timeout)──► CANCELLED
 * </pre>
 *
 * <p><b>Terminal states:</b> DELIVERED, CANCELLED, REFUNDED — no further transitions allowed.
 *
 * <p><b>Refund flow:</b> REFUNDED is set via Phase 10 (Return & Refund module) only.
 * It must never be set directly through the admin status endpoint.
 */
public enum OrderStatus {

    /** Order created; awaiting payment confirmation. Customer or timeout can cancel. */
    PENDING_PAYMENT,

    /** Payment confirmed (set by Razorpay webhook in Phase 7). */
    CONFIRMED,

    /** Order being picked/packed by warehouse. */
    PROCESSING,

    /** Order handed to courier. Tracking number / courier name may be set at this stage. */
    SHIPPED,

    /**
     * Courier has departed for final-mile delivery.
     * Set after {@code SHIPPED} — optional stage for couriers that provide
     * last-mile visibility (e.g. "Out for delivery" scan events).
     */
    OUT_FOR_DELIVERY,

    /** Successfully delivered to customer. Terminal. */
    DELIVERED,

    /** Cancelled before or during payment. Inventory restored. Terminal. */
    CANCELLED,

    /** Returned and refunded (Phase 10). Terminal. */
    REFUNDED;

    /**
     * Validates whether a transition from {@code current} to {@code next} is legal.
     * Throws {@link IllegalArgumentException} if the transition is invalid.
     *
     * <p>Legal admin transitions (forward only):
     * <pre>
     *   PENDING_PAYMENT  → CONFIRMED   (manual confirm — COD, bank transfer, or Phase 7 webhook)
     *   PENDING_PAYMENT  → CANCELLED   (admin cancels an unpaid order)
     *   CONFIRMED        → PROCESSING | CANCELLED
     *   PROCESSING       → SHIPPED
     *   SHIPPED          → OUT_FOR_DELIVERY | DELIVERED
     *   OUT_FOR_DELIVERY → DELIVERED
     * </pre>
     *
     * <p>Customer cancel: PENDING_PAYMENT → CANCELLED (handled separately in OrderService).
     */
    public static void assertValidAdminTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING_PAYMENT  -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED        -> next == PROCESSING || next == CANCELLED;
            case PROCESSING       -> next == SHIPPED;
            case SHIPPED          -> next == OUT_FOR_DELIVERY || next == DELIVERED;
            case OUT_FOR_DELIVERY -> next == DELIVERED;
            default               -> false; // DELIVERED, CANCELLED, REFUNDED are terminal
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + current + " → " + next +
                    ". Allowed admin transitions: PENDING_PAYMENT→CONFIRMED|CANCELLED, " +
                    "CONFIRMED→PROCESSING|CANCELLED, PROCESSING→SHIPPED, " +
                    "SHIPPED→OUT_FOR_DELIVERY|DELIVERED, OUT_FOR_DELIVERY→DELIVERED.");
        }
    }
}

