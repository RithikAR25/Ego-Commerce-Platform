package com.ego.raw_ego.notification.enums;

/**
 * All distinct notification trigger events in the EGO platform.
 *
 * <p>Stored as a VARCHAR in {@code notification_logs.event_type} so new values
 * can be added without a schema migration (ddl-auto=update handles new ENUMs
 * only when the column definition uses VARCHAR — see NotificationLog entity).
 */
public enum NotificationEventType {

    /** Customer placed an order — cart → PENDING_PAYMENT. */
    ORDER_PLACED,

    /** Razorpay webhook confirmed payment — PENDING_PAYMENT → CONFIRMED. */
    PAYMENT_CONFIRMED,

    /** Email address verification link sent on registration or re-send request. */
    EMAIL_VERIFICATION,

    /** Admin advanced order status → SHIPPED. */
    ORDER_SHIPPED,

    /** Admin advanced order status → DELIVERED. */
    ORDER_DELIVERED,

    /** Admin approved a return request and the Razorpay refund was processed successfully. */
    REFUND_COMPLETED,

    /** Password reset link sent when the user requests to recover their account. */
    PASSWORD_RESET,

    /**
     * A product the user wishlisted has gone out of stock.
     * Fires when the product transitions ACTIVE → OUT_OF_STOCK.
     */
    WISHLIST_OUT_OF_STOCK,

    /**
     * A product the user wishlisted is back in stock.
     * Fires when the product transitions OUT_OF_STOCK → ACTIVE.
     */
    WISHLIST_BACK_IN_STOCK
}
