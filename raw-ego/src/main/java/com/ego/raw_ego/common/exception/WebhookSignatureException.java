package com.ego.raw_ego.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a Razorpay webhook HMAC-SHA256 signature is invalid.
 *
 * <h3>Why 400 Bad Request (not 409 Conflict)?</h3>
 * <p>Razorpay's webhook retry policy retries on 5xx responses (server errors).
 * It does NOT retry on 4xx (client errors — meaning the payload itself is rejected).
 * Returning 400 here is intentional: a bad signature means the request is malformed
 * or tampered, and we never want a retry of a forgery attempt.
 *
 * <p>Using a dedicated exception type (rather than {@link ConflictException}) also
 * makes the GlobalExceptionHandler mapping explicit and auditable.
 */
public class WebhookSignatureException extends EgoException {

    public WebhookSignatureException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
