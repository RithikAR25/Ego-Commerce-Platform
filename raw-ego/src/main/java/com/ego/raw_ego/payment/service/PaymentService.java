package com.ego.raw_ego.payment.service;

import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.exception.WebhookSignatureException;
import com.ego.raw_ego.notification.event.PaymentConfirmedEvent;
import com.ego.raw_ego.order.entity.Order;
import com.ego.raw_ego.order.entity.OrderStatusHistory;
import com.ego.raw_ego.order.enums.OrderStatus;
import com.ego.raw_ego.order.repository.OrderRepository;
import com.ego.raw_ego.payment.dto.response.PaymentOrderResponse;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Business logic for the Razorpay payment lifecycle.
 *
 * <h3>Payment flow (CRITICAL — read before modifying)</h3>
 * <ol>
 *   <li>Customer places EGO order → {@code PENDING_PAYMENT}.</li>
 *   <li>Frontend calls {@code POST /api/v1/payments/razorpay/create} →
 *       this service creates a Razorpay order and stores {@code razorpay_order_id} on the EGO order.</li>
 *   <li>Frontend opens Checkout.js modal using {@code razorpay_order_id}.</li>
 *   <li>Customer pays → Razorpay fires {@code POST /api/v1/webhooks/razorpay}.</li>
 *   <li>{@link #handleWebhook} verifies HMAC-SHA256 signature, then advances order to {@code CONFIRMED}.</li>
 * </ol>
 *
 * <h3>Architecture rules observed</h3>
 * <ul>
 *   <li>Razorpay HTTP calls are made OUTSIDE {@code @Transactional} — per ARCHITECTURE_RULES.md.</li>
 *   <li>Only the DB write in {@link #handleWebhook} is inside a transaction.</li>
 *   <li>The webhook handler is idempotent — duplicate events are silently ignored.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final RazorpayClient   razorpayClient;
    private final OrderRepository  orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    @Value("${razorpay.currency}")
    private String currency;

    // ── Create Razorpay order ─────────────────────────────────────────────────

    /**
     * Creates a Razorpay payment order for an existing EGO order.
     *
     * <p>Validates that:
     * <ul>
     *   <li>The EGO order exists and belongs to the requesting user.</li>
     *   <li>The EGO order is in {@code PENDING_PAYMENT} status.</li>
     *   <li>No Razorpay order has been created yet (idempotency guard).</li>
     * </ul>
     *
     * <p><b>NOT inside a transaction</b> — the Razorpay API call must not hold a DB connection.
     *
     * @param egoOrderId the EGO order to create a payment for
     * @param userId     the authenticated user — used for ownership check
     * @return response containing the Razorpay order ID and fields needed for Checkout.js
     */
    public PaymentOrderResponse createRazorpayOrder(Long egoOrderId, Long userId) {
        // Load EGO order — validates existence + ownership
        Order order = orderRepository.findByIdAndUserId(egoOrderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: id=" + egoOrderId));

        // Validate status
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(
                    "Cannot create payment for order id=" + egoOrderId +
                    ". Status must be PENDING_PAYMENT, got: " + order.getStatus());
        }

        // Idempotency: if a Razorpay order was already created, return it
        if (order.getRazorpayOrderId() != null) {
            log.info("Razorpay order already exists for egoOrderId={}: {}",
                    egoOrderId, order.getRazorpayOrderId());
            return buildResponse(order, order.getRazorpayOrderId());
        }

        // Amount in paise (₹1 = 100 paise) — Razorpay requires integer paise
        long amountInPaise = order.getGrandTotal()
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValue();

        // Call Razorpay Orders API (outside @Transactional as per architecture rules)
        com.razorpay.Order razorpayOrder = callRazorpayCreateOrder(egoOrderId, amountInPaise);

        // Persist the razorpay_order_id on the EGO order
        persistRazorpayOrderId(egoOrderId, razorpayOrder.get("id").toString());

        log.info("Razorpay order created: egoOrderId={} razorpayOrderId={}",
                egoOrderId, razorpayOrder.get("id"));

        return buildResponse(order, razorpayOrder.get("id").toString());
    }

    // ── Webhook handler ───────────────────────────────────────────────────────

    /**
     * Processes an incoming Razorpay webhook event.
     *
     * <h3>Security</h3>
     * <p>The HMAC-SHA256 signature on {@code X-Razorpay-Signature} is verified BEFORE any
     * business logic runs. A signature mismatch throws {@link WebhookSignatureException}
     * (mapped to 400 Bad Request). Returning 400 is intentional — Razorpay only retries
     * on 5xx, so a 400 signals "this request is rejected, do not retry".
     *
     * <h3>Idempotency — duplicate deliveries return 200 OK</h3>
     * <p>If the EGO order is already {@code CONFIRMED} when a duplicate {@code payment.captured}
     * arrives, the handler skips all side effects and returns normally. The controller
     * always returns {@code 200 OK} — the correct production-grade response for duplicate
     * webhook delivery. Razorpay retries webhooks until it receives a 2xx; returning
     * anything other than 2xx for a valid duplicate would cause unnecessary re-deliveries.
     *
     * <h3>Transaction scope</h3>
     * <p>Only the DB write (status update + history insert) is inside a transaction.
     * Signature verification happens before the transaction opens.
     *
     * @param rawBody           the raw request body bytes (preserved exactly as signed by Razorpay)
     * @param razorpaySignature the value of the {@code X-Razorpay-Signature} header
     */
    public void handleWebhook(String rawBody, String razorpaySignature) {
        // 1. Verify HMAC-SHA256 signature — MANDATORY before any logic
        verifyWebhookSignature(rawBody, razorpaySignature);

        // 2. Parse event
        JSONObject payload   = new JSONObject(rawBody);
        String event         = payload.getString("event");
        log.info("Razorpay webhook received: event={}", event);

        // 3. Only process payment.captured (ignore payment.failed, refund.*, etc.)
        if (!"payment.captured".equals(event)) {
            log.info("Ignoring non-captured webhook event: {}", event);
            return;
        }

        // 4. Extract IDs from payload
        JSONObject paymentEntity = payload
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String razorpayOrderId   = paymentEntity.getString("order_id");
        String razorpayPaymentId = paymentEntity.getString("id");

        log.info("Payment captured: razorpayOrderId={} razorpayPaymentId={}",
                razorpayOrderId, razorpayPaymentId);

        // 5. Confirm the EGO order (transactional DB write)
        confirmOrder(razorpayOrderId, razorpayPaymentId);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Verifies the HMAC-SHA256 signature provided by Razorpay on every webhook call.
     *
     * <h3>Why we don't use Razorpay SDK's {@code Utils.verifyWebhookSignature}</h3>
     * <p>The SDK method calls {@code payload.getBytes()} and {@code secret.getBytes()} without
     * specifying a charset — these use the JVM platform default, which varies by OS.
     * We use explicit {@link StandardCharsets#UTF_8} for both to guarantee consistency
     * regardless of environment.
     *
     * <p>Throws {@link WebhookSignatureException} (HTTP 400 Bad Request) on mismatch.
     * Returns 400 so Razorpay does NOT retry (retries only on 5xx).
     */
    private void verifyWebhookSignature(String rawBody, String razorpaySignature) {
        try {
            // Compute HMAC-SHA256(webhookSecret, rawBody) with explicit UTF-8
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hashBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));

            // Convert to lowercase hex (same format Razorpay sends)
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String computedSignature = sb.toString();

            if (!computedSignature.equals(razorpaySignature)) {
                log.warn("[Webhook HMAC] Signature mismatch — request rejected");
                throw new WebhookSignatureException("Invalid Razorpay webhook signature.");
            }

            log.debug("[Webhook HMAC] Signature verified OK");

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[Webhook HMAC] Crypto error: {}", e.getMessage());
            throw new WebhookSignatureException("Webhook signature verification error: " + e.getMessage());
        }
    }

    /**
     * Calls the Razorpay Orders API to create a new payment order.
     * Wrapped in a helper so the Razorpay exception is translated to a runtime exception.
     */
    private com.razorpay.Order callRazorpayCreateOrder(Long egoOrderId, long amountInPaise) {
        try {
            JSONObject options = new JSONObject();
            options.put("amount",   amountInPaise);
            options.put("currency", currency);
            // receipt is a short reference label shown in Razorpay dashboard — max 40 chars
            options.put("receipt",  "ego-order-" + egoOrderId);
            return razorpayClient.orders.create(options);
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed for egoOrderId={}: {}", egoOrderId, e.getMessage());
            throw new ConflictException(
                    "Payment gateway error: unable to create payment order. Please try again.");
        }
    }

    /**
     * Persists the {@code razorpay_order_id} onto the EGO {@link Order} row.
     * Runs in its own transaction so the ID is committed before the frontend receives it.
     */
    @Transactional
    public void persistRazorpayOrderId(Long egoOrderId, String razorpayOrderId) {
        Order order = orderRepository.findById(egoOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: id=" + egoOrderId));
        order.setRazorpayOrderId(razorpayOrderId);
        orderRepository.save(order);
    }

    /**
     * Advances the EGO order to {@code CONFIRMED} and stores the Razorpay payment ID.
     *
     * <p>Idempotent: if the order is already {@code CONFIRMED}, returns without changes.
     * This protects against duplicate webhook deliveries.
     */
    @Transactional
    public void confirmOrder(String razorpayOrderId, String razorpayPaymentId) {
        Order order = orderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No EGO order found for razorpayOrderId=" + razorpayOrderId));

        // Idempotency guard — Razorpay may fire the same webhook more than once.
        // Return normally (no exception) so the controller returns 200 OK.
        // 200 on duplicates is production-correct: Razorpay retries until it gets 2xx,
        // so throwing here would cause unnecessary re-delivery loops.
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("[Webhook] Duplicate delivery detected (idempotent skip): "
                    + "razorpayOrderId={} orderId={} — already CONFIRMED, returning 200 OK",
                    razorpayOrderId, order.getId());
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.warn("Unexpected status for confirmation: orderId={} status={}",
                    order.getId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setRazorpayPaymentId(razorpayPaymentId);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.CONFIRMED)
                .note("Payment confirmed via Razorpay. Payment ID: " + razorpayPaymentId)
                .build();
        order.addStatusHistory(history);

        orderRepository.saveAndFlush(order);

        log.info("Order CONFIRMED: egoOrderId={} razorpayPaymentId={}",
                order.getId(), razorpayPaymentId);

        // Publish event AFTER saveAndFlush — CONFIRMED status + razorpay_payment_id
        // are committed to DB before the async listener reads them.
        // The async listener sends the payment confirmation email (Phase 8).
        eventPublisher.publishEvent(
                new PaymentConfirmedEvent(this, order.getId(), order.getUser().getId()));
    }

    private PaymentOrderResponse buildResponse(Order order, String razorpayOrderId) {
        long amountInPaise = order.getGrandTotal()
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValue();
        return PaymentOrderResponse.builder()
                .razorpayOrderId(razorpayOrderId)
                .amount(amountInPaise)
                .currency(currency)
                .keyId(keyId)
                .egoOrderId(order.getId())
                .build();
    }
}
