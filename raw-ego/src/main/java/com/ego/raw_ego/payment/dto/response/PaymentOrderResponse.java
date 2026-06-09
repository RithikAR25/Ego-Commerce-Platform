package com.ego.raw_ego.payment.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Response from {@code POST /api/v1/payments/razorpay/create}.
 *
 * <p>The frontend uses these fields to open the Razorpay Checkout.js modal:
 * <ul>
 *   <li>{@code razorpayOrderId} — passed as {@code options.order_id} to Razorpay SDK</li>
 *   <li>{@code amount} — in paise (₹ × 100); shown in the payment sheet</li>
 *   <li>{@code currency} — "INR"</li>
 *   <li>{@code keyId} — public Razorpay key (safe to send to browser)</li>
 *   <li>{@code egoOrderId} — used by the frontend to reference the EGO order after payment</li>
 * </ul>
 *
 * <p><b>Security note:</b> {@code keyId} is the PUBLIC identifier (starts with {@code rzp_test_}).
 * The {@code keySecret} is NEVER returned to the client — it stays in the backend only.
 */
@Getter
@Builder
public class PaymentOrderResponse {

    /** Razorpay order ID — e.g. "order_XXXXXXXXXXXXXXXXXX". */
    private String razorpayOrderId;

    /** Amount in paise (₹1 = 100 paise). */
    private long amount;

    /** ISO 4217 currency code — "INR". */
    private String currency;

    /** Public Razorpay key ID — safe to expose to the browser. */
    private String keyId;

    /** EGO order ID — for frontend reference after payment completes. */
    private Long egoOrderId;
}
