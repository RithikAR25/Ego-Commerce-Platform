package com.ego.raw_ego.notification.service;

import com.ego.raw_ego.auth.entity.User;

/**
 * Contract for building and dispatching transactional emails via the SendGrid Web API.
 *
 * <h3>Architecture rules</h3>
 * <ul>
 *   <li>NOT {@code @Transactional} — SendGrid HTTP calls must never hold a DB connection.</li>
 *   <li>Errors are caught internally — failures logged to {@code notification_logs}, never propagated.</li>
 *   <li>Idempotency checked before every send via {@link NotificationLogService#alreadySent}.</li>
 * </ul>
 */
public interface NotificationService {

    /** Sends an order confirmation email (status: {@code PENDING_PAYMENT}). */
    void sendOrderConfirmation(Long orderId);

    /** Sends a payment confirmation email (status: {@code CONFIRMED}). */
    void sendPaymentConfirmation(Long orderId);

    /**
     * Sends an email verification link to a newly registered user.
     * Idempotency is NOT checked here — re-sends are explicitly triggered by the user.
     */
    void sendEmailVerification(Long userId, String email, String firstName, String verificationLink);

    /** Sends a shipping notification email (status: {@code SHIPPED}). */
    void sendOrderShipped(Long orderId);

    /** Sends a delivery confirmation email with review CTA and return window reminder. */
    void sendOrderDelivered(Long orderId);

    /** Sends a refund confirmation email after a successful Razorpay refund. */
    void sendRefundCompleted(Long orderId, String razorpayRefundId);

    /** Sends a password reset email containing a 1-hour JWT link. */
    void sendPasswordResetEmail(Long userId, String email, String firstName, String resetLink);

    /** Sends a wishlist "out of stock" alert to the given user. */
    void sendWishlistOutOfStock(User user, Long productId, String productName);

    /** Sends a wishlist "back in stock" alert to the given user. */
    void sendWishlistBackInStock(User user, Long productId, String productName);
}
