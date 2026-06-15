package com.ego.raw_ego.payment.service;

import com.ego.raw_ego.payment.dto.response.PaymentOrderResponse;

/**
 * Contract for the Razorpay payment lifecycle.
 *
 * <h3>Payment flow</h3>
 * <ol>
 *   <li>Customer places EGO order → {@code PENDING_PAYMENT}.</li>
 *   <li>Frontend calls {@code POST /api/v1/payments/razorpay/create} →
 *       this service creates a Razorpay order and stores {@code razorpay_order_id} on the EGO order.</li>
 *   <li>Frontend opens Checkout.js modal using {@code razorpay_order_id}.</li>
 *   <li>Customer pays → Razorpay fires {@code POST /api/v1/webhooks/razorpay}.</li>
 *   <li>{@link #handleWebhook} verifies HMAC-SHA256 signature, then advances order to {@code CONFIRMED}.</li>
 * </ol>
 *
 * <p><b>Architecture rules:</b> Razorpay HTTP calls are made OUTSIDE {@code @Transactional}.
 * Only DB writes are inside a transaction. The webhook handler is idempotent.
 */
public interface PaymentService {

    /**
     * Creates a Razorpay payment order for an existing EGO order.
     * NOT inside a transaction — the Razorpay API call must not hold a DB connection.
     *
     * @param egoOrderId the EGO order to create a payment for
     * @param userId     the authenticated user — used for ownership check
     * @return response containing the Razorpay order ID and fields needed for Checkout.js
     */
    PaymentOrderResponse createRazorpayOrder(Long egoOrderId, Long userId);

    /**
     * Processes an incoming Razorpay webhook event.
     * Verifies HMAC-SHA256 signature, then advances order to {@code CONFIRMED}.
     * Idempotent — duplicate events are silently ignored.
     *
     * @param rawBody           the raw request body bytes (preserved exactly as signed by Razorpay)
     * @param razorpaySignature the value of the {@code X-Razorpay-Signature} header
     * @throws com.ego.raw_ego.common.exception.WebhookSignatureException on signature mismatch
     */
    void handleWebhook(String rawBody, String razorpaySignature);

    /**
     * Transactional step: persists the Razorpay order ID on the EGO order.
     */
    void persistRazorpayOrderId(Long egoOrderId, String razorpayOrderId);

    /**
     * Transactional step: advances the EGO order to CONFIRMED and persists payment IDs.
     */
    void confirmOrder(String razorpayOrderId, String razorpayPaymentId);
}
