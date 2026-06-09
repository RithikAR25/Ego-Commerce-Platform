package com.ego.raw_ego.payment.controller;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.response.ApiResponse;
import com.ego.raw_ego.payment.dto.request.CreatePaymentOrderRequest;
import com.ego.raw_ego.payment.dto.response.PaymentOrderResponse;
import com.ego.raw_ego.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * REST controller for the payment domain (Razorpay integration).
 *
 * <pre>
 * ── Customer (JWT required) ────────────────────────────────────────────────
 * POST   /api/v1/payments/razorpay/create   → Create a Razorpay order for an EGO order
 *
 * ── Public (HMAC-verified, no JWT) ────────────────────────────────────────
 * POST   /api/v1/webhooks/razorpay           → Receive Razorpay payment events
 * </pre>
 *
 * <h3>Webhook security</h3>
 * <p>The webhook endpoint is in {@code PUBLIC_MATCHERS} in SecurityConfig (no JWT filter).
 * Security is enforced entirely by HMAC-SHA256 signature verification inside
 * {@link PaymentService#handleWebhook}. Never skip this verification.
 */
@RestController
@Tag(name = "Payments", description = "Razorpay payment order creation and webhook processing")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService  paymentService;
    private final UserRepository  userRepository;

    // ── POST /api/v1/payments/razorpay/create ─────────────────────────────────

    @PostMapping("/api/v1/payments/razorpay/create")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary     = "Create Razorpay payment order",
            description = "Creates a Razorpay payment order for an existing PENDING_PAYMENT EGO order. " +
                          "Returns the razorpay_order_id and other fields required to open Checkout.js. " +
                          "Idempotent — calling twice returns the same Razorpay order."
    )
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createPaymentOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreatePaymentOrderRequest request
    ) {
        Long userId = resolveUserId(userDetails);
        PaymentOrderResponse response = paymentService.createRazorpayOrder(request.orderId(), userId);
        return ResponseEntity.ok(ApiResponse.success("Payment order created.", response));
    }

    // ── POST /api/v1/webhooks/razorpay ────────────────────────────────────────

    @PostMapping("/api/v1/webhooks/razorpay")
    @Operation(
            summary     = "Razorpay webhook receiver",
            description = "Receives payment lifecycle events from Razorpay. " +
                          "Secured by HMAC-SHA256 signature verification on X-Razorpay-Signature header — " +
                          "NOT by JWT. Processes payment.captured events to advance orders to CONFIRMED. " +
                          "Idempotent — duplicate events are safely ignored."
    )
    public ResponseEntity<Void> handleWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) throws IOException {
        // Read raw bytes directly — avoids Spring StringHttpMessageConverter charset ambiguity.
        // The exact bytes received are used for HMAC — same as what Razorpay signed.
        byte[] rawBytes = request.getInputStream().readAllBytes();
        String rawBody  = new String(rawBytes, StandardCharsets.UTF_8);

        log.info("Razorpay webhook received — body_bytes={} sig_header_present={}",
                rawBytes.length, signature != null);

        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmailAndDeletedFalse(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
