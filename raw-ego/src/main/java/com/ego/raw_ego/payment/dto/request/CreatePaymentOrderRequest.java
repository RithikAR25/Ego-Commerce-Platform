package com.ego.raw_ego.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for {@code POST /api/v1/payments/razorpay/create}.
 *
 * <p>The client provides the EGO order ID (already persisted in DB as PENDING_PAYMENT).
 * The backend creates a corresponding Razorpay order and returns the {@code razorpay_order_id}
 * needed to open the Checkout.js modal.
 */
public record CreatePaymentOrderRequest(

        @NotNull(message = "orderId is required")
        @Positive(message = "orderId must be a positive integer")
        Long orderId
) {}
