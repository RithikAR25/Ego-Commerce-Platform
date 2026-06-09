package com.ego.raw_ego.notification.service;

import com.ego.raw_ego.notification.enums.NotificationEventType;
import com.ego.raw_ego.order.entity.Order;
import com.ego.raw_ego.order.entity.OrderItem;
import com.ego.raw_ego.order.repository.OrderRepository;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Core email notification service — builds and dispatches transactional emails
 * via the SendGrid Web API.
 *
 * <h3>Architecture rules observed</h3>
 * <ul>
 *   <li>NOT {@code @Transactional} — SendGrid HTTP calls must never hold a DB connection.</li>
 *   <li>All DB reads use {@code findByIdWithUserAndItems} (JOIN FETCH) so the User and
 *       items are fully initialized before the session closes — safe on any async thread.</li>
 *   <li>Errors are caught internally — failures logged to {@code notification_logs},
 *       never propagated to the caller.</li>
 *   <li>Idempotency checked before every send via {@link NotificationLogService#alreadySent}.</li>
 * </ul>
 *
 * <h3>HTML building strategy</h3>
 * <p>All HTML is built by direct {@code StringBuilder.append()} calls — dynamic values are
 * appended as plain string concatenation. This deliberately avoids Java's
 * {@code String.format()} / {@code .formatted()} for HTML content, which is brittle:
 * any literal {@code %} character in CSS (e.g. {@code width="100%"}) or in user data
 * causes {@code UnknownFormatConversionException}. Direct append has no such risk.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final SendGrid                sendGrid;
    private final OrderRepository         orderRepository;
    private final NotificationLogService  notificationLogService;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${sendgrid.from-name}")
    private String fromName;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                             .withZone(ZoneId.of("Asia/Kolkata"));

    // ── Public send methods ───────────────────────────────────────────────────

    /**
     * Sends an order confirmation email when a customer places an order
     * (status: {@code PENDING_PAYMENT}).
     *
     * <p>Checks idempotency first — if a SUCCESS log already exists for this
     * (orderId, ORDER_PLACED) pair, the send is skipped silently.
     *
     * @param orderId the EGO order ID (must already be committed to DB)
     */
    public void sendOrderConfirmation(Long orderId) {
        if (notificationLogService.alreadySent(orderId, NotificationEventType.ORDER_PLACED)) {
            log.info("[Notification] ORDER_PLACED already sent for orderId={} — skipping", orderId);
            return;
        }

        // JOIN FETCH loads User + items in one query — prevents LazyInitializationException
        // on the async ego-async-* thread where the HTTP session is no longer active.
        Order order = orderRepository.findByIdWithUserAndItems(orderId).orElse(null);
        if (order == null) {
            log.warn("[Notification] Order not found for ORDER_PLACED notification: orderId={}", orderId);
            notificationLogService.logFailure(orderId, "unknown",
                    NotificationEventType.ORDER_PLACED,
                    "Order not found in DB at notification time");
            return;
        }

        String recipientEmail = order.getUser().getEmail();
        String subject = "Your EGO order #" + orderId + " has been received!";
        String htmlBody = buildOrderPlacedHtml(order);

        dispatch(orderId, recipientEmail, subject, htmlBody, NotificationEventType.ORDER_PLACED);
    }

    /**
     * Sends a payment confirmation email when Razorpay confirms payment
     * (status: {@code CONFIRMED}).
     *
     * @param orderId the EGO order ID (must already be CONFIRMED in DB)
     */
    public void sendPaymentConfirmation(Long orderId) {
        if (notificationLogService.alreadySent(orderId, NotificationEventType.PAYMENT_CONFIRMED)) {
            log.info("[Notification] PAYMENT_CONFIRMED already sent for orderId={} — skipping", orderId);
            return;
        }

        // JOIN FETCH loads User + items in one query — prevents LazyInitializationException
        // on the async ego-async-* thread where the HTTP session is no longer active.
        Order order = orderRepository.findByIdWithUserAndItems(orderId).orElse(null);
        if (order == null) {
            log.warn("[Notification] Order not found for PAYMENT_CONFIRMED notification: orderId={}", orderId);
            notificationLogService.logFailure(orderId, "unknown",
                    NotificationEventType.PAYMENT_CONFIRMED,
                    "Order not found in DB at notification time");
            return;
        }

        String recipientEmail = order.getUser().getEmail();
        String subject = "Payment confirmed — EGO order #" + orderId + " is being prepared";
        String htmlBody = buildPaymentConfirmedHtml(order);

        dispatch(orderId, recipientEmail, subject, htmlBody, NotificationEventType.PAYMENT_CONFIRMED);
    }

    /**
     * Sends an email verification link to a newly registered user (or on re-send request).
     *
     * <p>The verification link embeds a signed JWT token ({@code type=EMAIL_VERIFY}, 24h TTL)
     * generated by {@link com.ego.raw_ego.auth.service.JwtService#generateEmailVerificationToken}.
     * Clicking the link calls {@code POST /api/v1/auth/verify-email?token=...}.
     *
     * <p>Idempotency is NOT checked here — re-sends are explicitly triggered by the user.
     * The underlying {@code emailVerified=true} write in {@code AuthService.verifyEmail()}
     * is idempotent anyway.
     *
     * @param userId    used as the log reference (no orderId equivalent here)
     * @param email     the recipient's email address
     * @param firstName the recipient's first name for personalisation
     * @param verificationLink the full verification URL: {@code https://.../?token=<jwt>}
     */
    public void sendEmailVerification(Long userId, String email, String firstName, String verificationLink) {
        String subject  = "Please verify your EGO account email";
        String htmlBody = buildEmailVerificationHtml(firstName, verificationLink);

        // Use userId * -1 as a pseudo orderId key so existing NotificationLogService
        // can store the log without schema changes (orderId column is reused as a reference).
        dispatch(-userId, email, subject, htmlBody, NotificationEventType.EMAIL_VERIFICATION);
    }

    /**
     * Sends a shipping notification email when an admin advances the order to {@code SHIPPED}.
     *
     * @param orderId the EGO order ID (must already be SHIPPED in DB)
     */
    public void sendOrderShipped(Long orderId) {
        if (notificationLogService.alreadySent(orderId, NotificationEventType.ORDER_SHIPPED)) {
            log.info("[Notification] ORDER_SHIPPED already sent for orderId={} — skipping", orderId);
            return;
        }

        Order order = orderRepository.findByIdWithUserAndItems(orderId).orElse(null);
        if (order == null) {
            log.warn("[Notification] Order not found for ORDER_SHIPPED notification: orderId={}", orderId);
            notificationLogService.logFailure(orderId, "unknown",
                    NotificationEventType.ORDER_SHIPPED, "Order not found in DB at notification time");
            return;
        }

        String recipientEmail = order.getUser().getEmail();
        String subject        = "Your EGO order #" + orderId + " is on its way!";
        String htmlBody       = buildOrderShippedHtml(order);

        dispatch(orderId, recipientEmail, subject, htmlBody, NotificationEventType.ORDER_SHIPPED);
    }

    /**
     * Sends a delivery confirmation email when an admin advances the order to {@code DELIVERED}.
     * Includes a review CTA and a 7-day return window reminder.
     *
     * @param orderId the EGO order ID (must already be DELIVERED in DB)
     */
    public void sendOrderDelivered(Long orderId) {
        if (notificationLogService.alreadySent(orderId, NotificationEventType.ORDER_DELIVERED)) {
            log.info("[Notification] ORDER_DELIVERED already sent for orderId={} — skipping", orderId);
            return;
        }

        Order order = orderRepository.findByIdWithUserAndItems(orderId).orElse(null);
        if (order == null) {
            log.warn("[Notification] Order not found for ORDER_DELIVERED notification: orderId={}", orderId);
            notificationLogService.logFailure(orderId, "unknown",
                    NotificationEventType.ORDER_DELIVERED, "Order not found in DB at notification time");
            return;
        }

        String recipientEmail = order.getUser().getEmail();
        String subject        = "Your EGO order #" + orderId + " has been delivered";
        String htmlBody       = buildOrderDeliveredHtml(order);

        dispatch(orderId, recipientEmail, subject, htmlBody, NotificationEventType.ORDER_DELIVERED);
    }

    /**
     * Sends a refund confirmation email after a Razorpay refund is successfully processed.
     * Includes the Razorpay refund ID and expected credit timeline (5–7 business days).
     *
     * @param orderId          the EGO order ID (must already be REFUNDED in DB)
     * @param razorpayRefundId the Razorpay refund reference (e.g. {@code rfnd_XXXXXXXXXX})
     */
    public void sendRefundCompleted(Long orderId, String razorpayRefundId) {
        if (notificationLogService.alreadySent(orderId, NotificationEventType.REFUND_COMPLETED)) {
            log.info("[Notification] REFUND_COMPLETED already sent for orderId={} — skipping", orderId);
            return;
        }

        Order order = orderRepository.findByIdWithUserAndItems(orderId).orElse(null);
        if (order == null) {
            log.warn("[Notification] Order not found for REFUND_COMPLETED notification: orderId={}", orderId);
            notificationLogService.logFailure(orderId, "unknown",
                    NotificationEventType.REFUND_COMPLETED, "Order not found in DB at notification time");
            return;
        }

        String recipientEmail = order.getUser().getEmail();
        String subject        = "Your EGO refund has been processed — order #" + orderId;
        String htmlBody       = buildRefundCompletedHtml(order, razorpayRefundId);

        dispatch(orderId, recipientEmail, subject, htmlBody, NotificationEventType.REFUND_COMPLETED);
    }

    /**
     * Sends a password reset email containing a 1-hour JWT link.
     *
     * <p>Uses {@code -userId - 1_000_000} as a pseudo orderId reference key in the
     * notification log to avoid collision with the email-verification pseudo-key
     * ({@code -userId}). This is an intentional reuse of the orderId column as
     * a generic reference key — acceptable at this scale.
     *
     * @param userId    used as the log reference
     * @param email     the recipient's email address
     * @param firstName the recipient's first name for personalisation
     * @param resetLink the full reset URL: {@code https://.../?token=<jwt>}
     */
    public void sendPasswordResetEmail(Long userId, String email, String firstName, String resetLink) {
        String subject  = "Reset your EGO account password";
        String htmlBody = buildPasswordResetHtml(firstName, resetLink);

        // Pseudo-key: -(userId + 1_000_000) avoids collision with email-verification (-userId)
        dispatch(-(userId + 1_000_000L), email, subject, htmlBody, NotificationEventType.PASSWORD_RESET);
    }

    // ── Core dispatch ─────────────────────────────────────────────────────────

    /**
     * Sends a wishlist "out of stock" alert to the given user.
     * Called by {@link com.ego.raw_ego.wishlist.service.WishlistStockNotificationListener}.
     *
     * @param user        the recipient
     * @param productId   product that went out of stock
     * @param productName display name for the email subject
     */
    public void sendWishlistOutOfStock(com.ego.raw_ego.auth.entity.User user,
                                       Long productId, String productName) {
        String subject  = "Heads up — \"" + productName + "\" is now out of stock";
        String htmlBody = buildWishlistStockHtml(user.getFirstName(), productName, false);
        // Pseudo-key: unique per (user, product, direction)
        dispatch(-(user.getId() * 10_000_000L + productId), user.getEmail(),
                subject, htmlBody, NotificationEventType.WISHLIST_OUT_OF_STOCK);
    }

    /**
     * Sends a wishlist "back in stock" alert to the given user.
     * Called by {@link com.ego.raw_ego.wishlist.service.WishlistStockNotificationListener}.
     *
     * @param user        the recipient
     * @param productId   product that came back in stock
     * @param productName display name for the email subject
     */
    public void sendWishlistBackInStock(com.ego.raw_ego.auth.entity.User user,
                                        Long productId, String productName) {
        String subject  = "Back in stock — \"" + productName + "\" is available again!";
        String htmlBody = buildWishlistStockHtml(user.getFirstName(), productName, true);
        dispatch(-(user.getId() * 10_000_000L + productId + 5_000_000L), user.getEmail(),
                subject, htmlBody, NotificationEventType.WISHLIST_BACK_IN_STOCK);
    }


    /**
     * Builds and sends a SendGrid email, then logs the result.
     * All exceptions are caught — failures never propagate to the async listener.
     */
    private void dispatch(Long orderId, String recipientEmail, String subject,
                          String htmlBody, NotificationEventType eventType) {
        try {
            Email from    = new Email(fromEmail, fromName);
            Email to      = new Email(recipientEmail);
            Content content = new Content("text/html", htmlBody);

            Mail mail = new Mail(from, subject, to, content);

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                String messageId = response.getHeaders().getOrDefault("X-Message-Id", "");
                notificationLogService.logSuccess(orderId, recipientEmail, eventType, messageId);
                log.info("[Notification] Email sent: orderId={} eventType={} to={} statusCode={}",
                        orderId, eventType, recipientEmail, response.getStatusCode());
            } else {
                String errorMsg = "SendGrid HTTP " + response.getStatusCode() +
                                  ": " + response.getBody();
                notificationLogService.logFailure(orderId, recipientEmail, eventType, errorMsg);
                log.warn("[Notification] SendGrid non-2xx: orderId={} eventType={} status={} body={}",
                        orderId, eventType, response.getStatusCode(), response.getBody());
            }

        } catch (IOException e) {
            String errorMsg = "IOException sending email: " + e.getMessage();
            notificationLogService.logFailure(orderId, recipientEmail, eventType, errorMsg);
            log.error("[Notification] IOException: orderId={} eventType={} error={}",
                    orderId, eventType, e.getMessage());
        } catch (Exception e) {
            String errorMsg = "Unexpected error: " + e.getMessage();
            notificationLogService.logFailure(orderId, recipientEmail, eventType, errorMsg);
            log.error("[Notification] Unexpected error: orderId={} eventType={} error={}",
                    orderId, eventType, e.getMessage());
        }
    }

    // ── HTML builders ─────────────────────────────────────────────────────────
    //
    // IMPORTANT: All HTML is built via StringBuilder.append() with direct string
    // concatenation for dynamic values. Do NOT use String.format() or .formatted()
    // here — any literal '%' in CSS (e.g. width="100%") or in user data will throw
    // UnknownFormatConversionException at runtime.

    private String buildOrderPlacedHtml(Order order) {
        StringBuilder sb = new StringBuilder();

        // ── Doctype + head + body open ────────────────────────────────────────
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"en\">");
        sb.append("<head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">");
        sb.append("<title>Order Received</title></head>");
        sb.append("<body style=\"margin:0;padding:0;background:#0f0f0f;font-family:'Helvetica Neue',Arial,sans-serif;\">");

        // ── Outer wrapper table ───────────────────────────────────────────────
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#0f0f0f;padding:40px 0;\">");
        sb.append("<tr><td align=\"center\">");

        // ── Card table ────────────────────────────────────────────────────────
        sb.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#1a1a1a;border-radius:12px;overflow:hidden;max-width:600px;\">");

        // Header
        sb.append("<tr><td style=\"background:linear-gradient(135deg,#c8a96e,#8b6914);padding:32px 40px;text-align:center;\">");
        sb.append("<h1 style=\"margin:0;color:#ffffff;font-size:28px;font-weight:900;letter-spacing:4px;\">EGO</h1>");
        sb.append("<p style=\"margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:13px;letter-spacing:2px;text-transform:uppercase;\">Premium Streetwear</p>");
        sb.append("</td></tr>");

        // Body
        sb.append("<tr><td style=\"padding:40px;\">");
        sb.append("<h2 style=\"margin:0 0 8px;color:#ffffff;font-size:22px;font-weight:700;\">Order Received!</h2>");
        sb.append("<p style=\"margin:0 0 24px;color:#aaaaaa;font-size:14px;line-height:1.6;\">Hi ")
          .append(escapeHtml(order.getUser().getFirstName()))
          .append(", thank you for your order. We've received it and will send another email once payment is confirmed.</p>");

        // Order meta row
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:24px;\">");
        sb.append("<tr>");
        sb.append("<td style=\"padding:12px 0;border-bottom:1px solid #2a2a2a;\">")
          .append("<span style=\"color:#888;font-size:12px;text-transform:uppercase;letter-spacing:1px;\">Order ID</span><br>")
          .append("<span style=\"color:#c8a96e;font-size:16px;font-weight:700;\">#").append(order.getId()).append("</span>")
          .append("</td>");
        sb.append("<td style=\"padding:12px 0;border-bottom:1px solid #2a2a2a;text-align:right;\">")
          .append("<span style=\"color:#888;font-size:12px;text-transform:uppercase;letter-spacing:1px;\">Order Date</span><br>")
          .append("<span style=\"color:#ffffff;font-size:14px;\">").append(DATE_FMT.format(order.getCreatedAt())).append("</span>")
          .append("</td>");
        sb.append("</tr></table>");

        // Items header
        sb.append("<p style=\"margin:0 0 12px;color:#ffffff;font-size:14px;font-weight:600;text-transform:uppercase;letter-spacing:1px;\">Order Items</p>");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:24px;\">");
        sb.append("<tr style=\"background:#252525;\">")
          .append("<td style=\"padding:10px 12px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px;\">Item</td>")
          .append("<td style=\"padding:10px 12px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px;text-align:center;\">Qty</td>")
          .append("<td style=\"padding:10px 12px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px;text-align:right;\">Price</td>")
          .append("</tr>");

        // Item rows
        for (OrderItem item : order.getItems()) {
            sb.append("<tr style=\"border-bottom:1px solid #252525;\">")
              .append("<td style=\"padding:14px 12px;\">")
              .append("<span style=\"color:#ffffff;font-size:13px;font-weight:600;display:block;\">").append(escapeHtml(item.getProductNameSnapshot())).append("</span>")
              .append("<span style=\"color:#888;font-size:12px;\">").append(escapeHtml(item.getVariantLabelSnapshot())).append("</span>")
              .append("</td>")
              .append("<td style=\"padding:14px 12px;color:#aaaaaa;font-size:13px;text-align:center;\">").append(item.getQuantity()).append("</td>")
              .append("<td style=\"padding:14px 12px;color:#c8a96e;font-size:13px;font-weight:600;text-align:right;\">₹").append(formatMoney(item.getLineTotal())).append("</td>")
              .append("</tr>");
        }

        // Totals
        sb.append("</table>");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:24px;\">");
        sb.append("<tr>")
          .append("<td style=\"padding:8px 0;color:#888;font-size:13px;\">Subtotal</td>")
          .append("<td style=\"padding:8px 0;color:#ffffff;font-size:13px;text-align:right;\">₹").append(formatMoney(order.getSubtotal())).append("</td>")
          .append("</tr>");
        sb.append("<tr>")
          .append("<td style=\"padding:8px 0;color:#888;font-size:13px;\">Shipping</td>")
          .append("<td style=\"padding:8px 0;color:#ffffff;font-size:13px;text-align:right;\">₹").append(formatMoney(order.getShippingTotal())).append("</td>")
          .append("</tr>");
        sb.append("<tr style=\"border-top:1px solid #2a2a2a;\">")
          .append("<td style=\"padding:14px 0 0;color:#ffffff;font-size:15px;font-weight:700;\">Grand Total</td>")
          .append("<td style=\"padding:14px 0 0;color:#c8a96e;font-size:15px;font-weight:700;text-align:right;\">₹").append(formatMoney(order.getGrandTotal())).append("</td>")
          .append("</tr>");
        sb.append("</table>");

        // Shipping address
        sb.append("<div style=\"background:#252525;border-radius:8px;padding:16px;margin-bottom:24px;\">")
          .append("<p style=\"margin:0 0 6px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px;\">Shipping To</p>")
          .append("<p style=\"margin:0;color:#ffffff;font-size:13px;\">").append(escapeHtml(order.getShippingAddress())).append("</p>")
          .append("</div>");

        // Next step callout
        sb.append("<div style=\"background:#1f1a0e;border:1px solid #c8a96e;border-radius:8px;padding:16px;margin-bottom:32px;\">")
          .append("<p style=\"margin:0;color:#c8a96e;font-size:13px;\">")
          .append("&#9203; <strong>Next step:</strong> Complete your payment to confirm the order. ")
          .append("You'll receive another email once we receive your payment confirmation.")
          .append("</p></div>");

        // Close body td, body tr, card table, wrapper td, wrapper tr, wrapper table
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style=\"padding:24px 40px;background:#111111;text-align:center;\">")
          .append("<p style=\"margin:0;color:#555;font-size:12px;\">&copy; 2026 EGO Premium Streetwear. All rights reserved.</p>")
          .append("<p style=\"margin:6px 0 0;color:#555;font-size:11px;\">This is an automated message — please do not reply.</p>")
          .append("</td></tr>");

        sb.append("</table>"); // card
        sb.append("</td></tr></table>"); // wrapper
        sb.append("</body></html>");

        return sb.toString();
    }

    private String buildPaymentConfirmedHtml(Order order) {
        StringBuilder sb = new StringBuilder();

        // ── Doctype + head + body open ────────────────────────────────────────
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"en\">");
        sb.append("<head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">");
        sb.append("<title>Payment Confirmed</title></head>");
        sb.append("<body style=\"margin:0;padding:0;background:#0f0f0f;font-family:'Helvetica Neue',Arial,sans-serif;\">");

        // ── Outer wrapper table ───────────────────────────────────────────────
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#0f0f0f;padding:40px 0;\">");
        sb.append("<tr><td align=\"center\">");

        // ── Card table ────────────────────────────────────────────────────────
        sb.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#1a1a1a;border-radius:12px;overflow:hidden;max-width:600px;\">");

        // Header
        sb.append("<tr><td style=\"background:linear-gradient(135deg,#c8a96e,#8b6914);padding:32px 40px;text-align:center;\">")
          .append("<h1 style=\"margin:0;color:#ffffff;font-size:28px;font-weight:900;letter-spacing:4px;\">EGO</h1>")
          .append("<p style=\"margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:13px;letter-spacing:2px;text-transform:uppercase;\">Premium Streetwear</p>")
          .append("</td></tr>");

        // Success banner
        sb.append("<tr><td style=\"background:#0d2e1a;padding:20px 40px;text-align:center;border-bottom:1px solid #1a4a2a;\">")
          .append("<p style=\"margin:0;font-size:16px;color:#4caf7d;\">&#10003; Payment Confirmed</p>")
          .append("</td></tr>");

        // Body
        sb.append("<tr><td style=\"padding:40px;\">");
        sb.append("<h2 style=\"margin:0 0 8px;color:#ffffff;font-size:22px;font-weight:700;\">We got your payment!</h2>");
        sb.append("<p style=\"margin:0 0 24px;color:#aaaaaa;font-size:14px;line-height:1.6;\">Hi ")
          .append(escapeHtml(order.getUser().getFirstName()))
          .append(", your payment has been confirmed and we're preparing your order right now.</p>");

        // Order + payment meta
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:24px;\">");
        sb.append("<tr>");
        sb.append("<td style=\"padding:12px 0;border-bottom:1px solid #2a2a2a;\">")
          .append("<span style=\"color:#888;font-size:12px;text-transform:uppercase;letter-spacing:1px;\">Order ID</span><br>")
          .append("<span style=\"color:#c8a96e;font-size:16px;font-weight:700;\">#").append(order.getId()).append("</span>")
          .append("</td>");
        String paymentId = order.getRazorpayPaymentId() != null ? order.getRazorpayPaymentId() : "—";
        sb.append("<td style=\"padding:12px 0;border-bottom:1px solid #2a2a2a;text-align:right;\">")
          .append("<span style=\"color:#888;font-size:12px;text-transform:uppercase;letter-spacing:1px;\">Payment ID</span><br>")
          .append("<span style=\"color:#4caf7d;font-size:13px;font-family:monospace;\">").append(escapeHtml(paymentId)).append("</span>")
          .append("</td>");
        sb.append("</tr></table>");

        // Items header
        sb.append("<p style=\"margin:0 0 12px;color:#ffffff;font-size:14px;font-weight:600;text-transform:uppercase;letter-spacing:1px;\">Order Summary</p>");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:24px;\">");
        sb.append("<tr style=\"background:#252525;\">")
          .append("<td style=\"padding:10px 12px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px;\">Item</td>")
          .append("<td style=\"padding:10px 12px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px;text-align:center;\">Qty</td>")
          .append("<td style=\"padding:10px 12px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px;text-align:right;\">Total</td>")
          .append("</tr>");

        // Item rows
        for (OrderItem item : order.getItems()) {
            sb.append("<tr style=\"border-bottom:1px solid #252525;\">")
              .append("<td style=\"padding:14px 12px;\">")
              .append("<span style=\"color:#ffffff;font-size:13px;font-weight:600;display:block;\">").append(escapeHtml(item.getProductNameSnapshot())).append("</span>")
              .append("<span style=\"color:#888;font-size:12px;\">").append(escapeHtml(item.getVariantLabelSnapshot())).append("</span>")
              .append("</td>")
              .append("<td style=\"padding:14px 12px;color:#aaaaaa;font-size:13px;text-align:center;\">").append(item.getQuantity()).append("</td>")
              .append("<td style=\"padding:14px 12px;color:#c8a96e;font-size:13px;font-weight:600;text-align:right;\">₹").append(formatMoney(item.getLineTotal())).append("</td>")
              .append("</tr>");
        }

        // Grand total
        sb.append("</table>");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:24px;\">");
        sb.append("<tr style=\"border-top:1px solid #2a2a2a;\">")
          .append("<td style=\"padding:14px 0;color:#ffffff;font-size:15px;font-weight:700;\">Amount Paid</td>")
          .append("<td style=\"padding:14px 0;color:#4caf7d;font-size:15px;font-weight:700;text-align:right;\">₹").append(formatMoney(order.getGrandTotal())).append("</td>")
          .append("</tr>");
        sb.append("</table>");

        // Shipping address
        sb.append("<div style=\"background:#252525;border-radius:8px;padding:16px;margin-bottom:24px;\">")
          .append("<p style=\"margin:0 0 6px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px;\">Shipping To</p>")
          .append("<p style=\"margin:0;color:#ffffff;font-size:13px;\">").append(escapeHtml(order.getShippingAddress())).append("</p>")
          .append("</div>");

        // What's next callout
        sb.append("<div style=\"background:#0d1f2e;border:1px solid #2a5f8a;border-radius:8px;padding:16px;margin-bottom:32px;\">")
          .append("<p style=\"margin:0;color:#64b5f6;font-size:13px;\">")
          .append("&#128230; <strong>What's next?</strong> Our team is now processing your order. ")
          .append("You'll receive shipping updates as your order progresses.")
          .append("</p></div>");

        // Close body td, body tr
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style=\"padding:24px 40px;background:#111111;text-align:center;\">")
          .append("<p style=\"margin:0;color:#555;font-size:12px;\">&copy; 2026 EGO Premium Streetwear. All rights reserved.</p>")
          .append("<p style=\"margin:6px 0 0;color:#555;font-size:11px;\">This is an automated message — please do not reply.</p>")
          .append("</td></tr>");

        sb.append("</table>"); // card
        sb.append("</td></tr></table>"); // wrapper
        sb.append("</body></html>");

        return sb.toString();
    }

    private String buildEmailVerificationHtml(String firstName, String verificationLink) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"en\">");
        sb.append("<head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">");
        sb.append("<title>Verify Your Email</title></head>");
        sb.append("<body style=\"margin:0;padding:0;background:#0f0f0f;font-family:'Helvetica Neue',Arial,sans-serif;\">");

        // Outer wrapper
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#0f0f0f;padding:40px 0;\">");
        sb.append("<tr><td align=\"center\">");

        // Card
        sb.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#1a1a1a;border-radius:12px;overflow:hidden;max-width:600px;\">");

        // Header
        sb.append("<tr><td style=\"background:linear-gradient(135deg,#c8a96e,#8b6914);padding:32px 40px;text-align:center;\">");
        sb.append("<h1 style=\"margin:0;color:#ffffff;font-size:28px;font-weight:900;letter-spacing:4px;\">EGO</h1>");
        sb.append("<p style=\"margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:13px;letter-spacing:2px;text-transform:uppercase;\">Premium Streetwear</p>");
        sb.append("</td></tr>");

        // Body
        sb.append("<tr><td style=\"padding:40px;\">");
        sb.append("<h2 style=\"margin:0 0 8px;color:#ffffff;font-size:22px;font-weight:700;\">Verify Your Email</h2>");
        sb.append("<p style=\"margin:0 0 24px;color:#aaaaaa;font-size:14px;line-height:1.6;\">Hi ")
          .append(escapeHtml(firstName))
          .append(", welcome to EGO! Please click the button below to verify your email address and unlock checkout.</p>");

        // CTA button
        sb.append("<table cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 24px;\"><tr><td>");
        sb.append("<a href=\"").append(escapeHtml(verificationLink)).append("\" ")
          .append("style=\"display:inline-block;padding:14px 32px;background:linear-gradient(135deg,#c8a96e,#8b6914);")
          .append("color:#ffffff;text-decoration:none;border-radius:8px;font-size:15px;font-weight:700;letter-spacing:1px;\">")
          .append("Verify My Email</a>");
        sb.append("</td></tr></table>");

        // Expiry notice
        sb.append("<p style=\"margin:0 0 16px;color:#888;font-size:13px;line-height:1.6;\">")
          .append("This link is valid for <strong style=\"color:#ffffff;\">24 hours</strong>. ")
          .append("If it expires, you can request a new one from your account settings.</p>");

        // Fallback link
        sb.append("<p style=\"margin:0;color:#555;font-size:12px;line-height:1.6;\">")
          .append("If the button above doesn't work, paste this URL into your browser:<br>")
          .append("<span style=\"color:#c8a96e;word-break:break-all;\">").append(escapeHtml(verificationLink)).append("</span>")
          .append("</p>");

        // Close body td + tr
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style=\"padding:24px 40px;background:#111111;text-align:center;\">")
          .append("<p style=\"margin:0;color:#555;font-size:12px;\">&copy; 2026 EGO Premium Streetwear. All rights reserved.</p>")
          .append("<p style=\"margin:6px 0 0;color:#555;font-size:11px;\">If you didn't create an EGO account, you can ignore this email.</p>")
          .append("</td></tr>");

        sb.append("</table>"); // card
        sb.append("</td></tr></table>"); // wrapper
        sb.append("</body></html>");

        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0.00";
        // Use BigDecimal formatting directly — avoids String.format() with its
        // format-specifier parsing which could conflict with HTML content.
        return String.format("%,.2f", amount);
    }

    /**
     * Minimal HTML escaping for user-supplied strings embedded in email HTML.
     * Prevents XSS in email clients that render HTML.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    // ── HTML builders: shipping / delivery / refund ───────────────────────────

    /**
     * Builds the shipping confirmation email HTML.
     * Tells the customer their order is on its way and sets expectations for delivery.
     */
    private String buildOrderShippedHtml(Order order) {
        String firstName = order.getUser().getFirstName();
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;background:#f9f9f9;font-family:Arial,sans-serif;'>");
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='background:#f9f9f9;padding:40px 0;'><tr><td align='center'>");
        sb.append("<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border:1px solid #e8e8e8;'>");

        // Header
        sb.append("<tr><td style='background:#000000;padding:32px 40px;'>");
        sb.append("<h1 style='margin:0;color:#ffffff;font-size:24px;font-weight:900;letter-spacing:4px;'>EGO</h1>");
        sb.append("</td></tr>");

        // Body
        sb.append("<tr><td style='padding:48px 40px;'>");
        sb.append("<h2 style='margin:0 0 8px 0;font-size:22px;font-weight:800;color:#111;'>Your order is on its way!</h2>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;'>Hi ").append(escapeHtml(firstName)).append(",</p>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;line-height:1.6;'>Great news — your EGO order <strong>#").append(order.getId()).append("</strong> has been shipped and is on its way to you. You can expect delivery within <strong>3–5 business days</strong>.</p>");

        // Order summary box
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #e8e8e8;margin-bottom:32px;'>");
        sb.append("<tr><td style='background:#f9f9f9;padding:16px 20px;border-bottom:1px solid #e8e8e8;'>");
        sb.append("<p style='margin:0;font-size:11px;font-weight:700;letter-spacing:2px;text-transform:uppercase;color:#888;'>Order Summary</p>");
        sb.append("</td></tr>");
        for (OrderItem item : order.getItems()) {
            sb.append("<tr><td style='padding:12px 20px;border-bottom:1px solid #f2f2f2;'>");
            sb.append("<span style='font-size:14px;color:#111;font-weight:600;'>").append(escapeHtml(item.getProductNameSnapshot())).append("</span>");
            sb.append("<span style='font-size:12px;color:#888;margin-left:8px;'>").append(escapeHtml(item.getVariantLabelSnapshot())).append("</span>");
            sb.append("<span style='float:right;font-size:14px;color:#111;font-weight:700;'>×").append(item.getQuantity()).append("</span>");
            sb.append("</td></tr>");
        }
        sb.append("</table>");

        sb.append("<p style='margin:0 0 16px 0;color:#555;font-size:14px;line-height:1.6;'>Once delivered, you'll receive another email from us. If you have any questions, reply to this email.</p>");
        sb.append("<p style='margin:0;color:#555;font-size:14px;'>— The EGO Team</p>");
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style='background:#f9f9f9;padding:24px 40px;border-top:1px solid #e8e8e8;'>");
        sb.append("<p style='margin:0;font-size:12px;color:#aaa;text-align:center;'>EGO Fashion · Shipping & Returns Policy applies</p>");
        sb.append("</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    /**
     * Builds the delivery confirmation email HTML.
     * Celebrates the delivery and prompts the customer to leave a review.
     * Also reminds them of the 7-day return window.
     */
    private String buildOrderDeliveredHtml(Order order) {
        String firstName = order.getUser().getFirstName();
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;background:#f9f9f9;font-family:Arial,sans-serif;'>");
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='background:#f9f9f9;padding:40px 0;'><tr><td align='center'>");
        sb.append("<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border:1px solid #e8e8e8;'>");

        // Header
        sb.append("<tr><td style='background:#000000;padding:32px 40px;'>");
        sb.append("<h1 style='margin:0;color:#ffffff;font-size:24px;font-weight:900;letter-spacing:4px;'>EGO</h1>");
        sb.append("</td></tr>");

        // Body
        sb.append("<tr><td style='padding:48px 40px;'>");
        sb.append("<h2 style='margin:0 0 8px 0;font-size:22px;font-weight:800;color:#111;'>Delivered.</h2>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;'>Hi ").append(escapeHtml(firstName)).append(",</p>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;line-height:1.6;'>Your EGO order <strong>#").append(order.getId()).append("</strong> has been delivered. We hope you love what you ordered.</p>");

        // Items delivered
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #e8e8e8;margin-bottom:32px;'>");
        sb.append("<tr><td style='background:#f9f9f9;padding:16px 20px;border-bottom:1px solid #e8e8e8;'>");
        sb.append("<p style='margin:0;font-size:11px;font-weight:700;letter-spacing:2px;text-transform:uppercase;color:#888;'>Items Delivered</p>");
        sb.append("</td></tr>");
        for (OrderItem item : order.getItems()) {
            sb.append("<tr><td style='padding:12px 20px;border-bottom:1px solid #f2f2f2;'>");
            sb.append("<span style='font-size:14px;color:#111;font-weight:600;'>").append(escapeHtml(item.getProductNameSnapshot())).append("</span>");
            sb.append("<span style='font-size:12px;color:#888;margin-left:8px;'>").append(escapeHtml(item.getVariantLabelSnapshot())).append("</span>");
            sb.append("</td></tr>");
        }
        sb.append("</table>");

        // Review CTA
        sb.append("<p style='margin:0 0 24px 0;color:#555;font-size:15px;line-height:1.6;'>Share your thoughts — your review helps other shoppers.</p>");
        sb.append("<table cellpadding='0' cellspacing='0' style='margin-bottom:32px;'><tr><td style='background:#000;padding:14px 32px;'>");
        sb.append("<a href='#' style='color:#fff;font-size:13px;font-weight:700;letter-spacing:2px;text-transform:uppercase;text-decoration:none;'>Write a Review</a>");
        sb.append("</td></tr></table>");

        // Return reminder
        sb.append("<p style='margin:0;color:#888;font-size:13px;line-height:1.6;border-top:1px solid #f2f2f2;padding-top:24px;'>Not happy with your order? You have <strong>7 days</strong> from delivery to initiate a return from your account page.</p>");
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style='background:#f9f9f9;padding:24px 40px;border-top:1px solid #e8e8e8;'>");
        sb.append("<p style='margin:0;font-size:12px;color:#aaa;text-align:center;'>EGO Fashion · Returns accepted within 7 days of delivery</p>");
        sb.append("</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    /**
     * Builds the refund confirmation email HTML.
     * Confirms the refund amount, shows the Razorpay refund ID, and sets timeline expectations.
     *
     * @param order            the refunded order (for order ID + user name)
     * @param razorpayRefundId the Razorpay refund reference ID
     */
    private String buildRefundCompletedHtml(Order order, String razorpayRefundId) {
        String firstName = order.getUser().getFirstName();
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;background:#f9f9f9;font-family:Arial,sans-serif;'>");
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='background:#f9f9f9;padding:40px 0;'><tr><td align='center'>");
        sb.append("<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border:1px solid #e8e8e8;'>");

        // Header
        sb.append("<tr><td style='background:#000000;padding:32px 40px;'>");
        sb.append("<h1 style='margin:0;color:#ffffff;font-size:24px;font-weight:900;letter-spacing:4px;'>EGO</h1>");
        sb.append("</td></tr>");

        // Body
        sb.append("<tr><td style='padding:48px 40px;'>");
        sb.append("<h2 style='margin:0 0 8px 0;font-size:22px;font-weight:800;color:#111;'>Your refund has been processed.</h2>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;'>Hi ").append(escapeHtml(firstName)).append(",</p>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;line-height:1.6;'>We've successfully processed your refund for order <strong>#").append(order.getId()).append("</strong>. The amount will be credited back to your original payment method within <strong>5–7 business days</strong>, depending on your bank.</p>");

        // Refund details box
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #e8e8e8;margin-bottom:32px;'>");
        sb.append("<tr><td style='background:#f9f9f9;padding:16px 20px;border-bottom:1px solid #e8e8e8;'>");
        sb.append("<p style='margin:0;font-size:11px;font-weight:700;letter-spacing:2px;text-transform:uppercase;color:#888;'>Refund Details</p>");
        sb.append("</td></tr>");
        sb.append("<tr><td style='padding:16px 20px;border-bottom:1px solid #f2f2f2;'>");
        sb.append("<span style='font-size:13px;color:#888;'>EGO Order</span>");
        sb.append("<span style='float:right;font-size:14px;color:#111;font-weight:700;'>#").append(order.getId()).append("</span>");
        sb.append("</td></tr>");
        sb.append("<tr><td style='padding:16px 20px;border-bottom:1px solid #f2f2f2;'>");
        sb.append("<span style='font-size:13px;color:#888;'>Refund Reference</span>");
        sb.append("<span style='float:right;font-size:13px;color:#555;font-family:monospace;'>").append(escapeHtml(razorpayRefundId)).append("</span>");
        sb.append("</td></tr>");
        sb.append("<tr><td style='padding:16px 20px;'>");
        sb.append("<span style='font-size:13px;color:#888;'>Expected Credit</span>");
        sb.append("<span style='float:right;font-size:14px;color:#111;font-weight:700;'>5–7 Business Days</span>");
        sb.append("</td></tr>");
        sb.append("</table>");

        sb.append("<p style='margin:0;color:#888;font-size:13px;line-height:1.6;'>If you have any questions about your refund, contact your bank with the refund reference above. We're sorry to see you go — we hope to serve you again.</p>");
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style='background:#f9f9f9;padding:24px 40px;border-top:1px solid #e8e8e8;'>");
        sb.append("<p style='margin:0;font-size:12px;color:#aaa;text-align:center;'>EGO Fashion · This refund was processed via Razorpay</p>");
        sb.append("</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    /**
     * Builds the password reset email HTML.
     * Contains a prominent CTA button, 1-hour expiry warning, and security note.
     *
     * @param firstName the recipient's first name
     * @param resetLink the full reset URL with JWT token
     */
    private String buildPasswordResetHtml(String firstName, String resetLink) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;background:#f9f9f9;font-family:Arial,sans-serif;'>");
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='background:#f9f9f9;padding:40px 0;'><tr><td align='center'>");
        sb.append("<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border:1px solid #e8e8e8;'>");

        // Header
        sb.append("<tr><td style='background:#000000;padding:32px 40px;'>");
        sb.append("<h1 style='margin:0;color:#ffffff;font-size:24px;font-weight:900;letter-spacing:4px;'>EGO</h1>");
        sb.append("</td></tr>");

        // Body
        sb.append("<tr><td style='padding:48px 40px;'>");
        sb.append("<h2 style='margin:0 0 8px 0;font-size:22px;font-weight:800;color:#111;'>Reset your password.</h2>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;'>Hi ").append(escapeHtml(firstName)).append(",</p>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;line-height:1.6;'>We received a request to reset your EGO account password. Click the button below to set a new password. This link is valid for <strong>1 hour</strong>.</p>");

        // CTA button
        sb.append("<table cellpadding='0' cellspacing='0' style='margin-bottom:32px;'><tr><td style='background:#000;padding:16px 40px;'>");
        sb.append("<a href='").append(resetLink).append("' style='color:#fff;font-size:13px;font-weight:700;letter-spacing:2px;text-transform:uppercase;text-decoration:none;'>Reset Password</a>");
        sb.append("</td></tr></table>");

        // Security note
        sb.append("<p style='margin:0 0 16px 0;color:#888;font-size:13px;line-height:1.6;border-top:1px solid #f2f2f2;padding-top:24px;'>If you didn't request a password reset, you can safely ignore this email. Your password will not change unless you click the link above.</p>");
        sb.append("<p style='margin:0;color:#aaa;font-size:12px;line-height:1.6;'>For security, this link expires in <strong>1 hour</strong>. If it has expired, request a new one from the login page.</p>");
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style='background:#f9f9f9;padding:24px 40px;border-top:1px solid #e8e8e8;'>");
        sb.append("<p style='margin:0;font-size:12px;color:#aaa;text-align:center;'>EGO Fashion · This email was sent because a password reset was requested for your account</p>");
        sb.append("</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    /**
     * Builds the wishlist stock-change notification email HTML.
     *
     * @param firstName   recipient's first name
     * @param productName product display name
     * @param backInStock {@code true} = back-in-stock; {@code false} = out-of-stock
     */
    private String buildWishlistStockHtml(String firstName, String productName, boolean backInStock) {
        StringBuilder sb = new StringBuilder();

        String bannerColor  = backInStock ? "#1a7a4a" : "#b45309"; // emerald : amber
        String badgeLabel   = backInStock ? "BACK IN STOCK" : "OUT OF STOCK";
        String headline     = backInStock
                ? "Great news — it's back!"
                : "One of your wishlist items is out of stock";
        String bodyText     = backInStock
                ? "\"" + escapeHtml(productName) + "\" is available again. Don't miss out — sizes sell fast."
                : "\"" + escapeHtml(productName) + "\" is currently out of stock. We'll let you know as soon as it's available again.";
        String ctaLabel     = backInStock ? "Shop Now" : "View Wishlist";
        String ctaPath      = backInStock ? "/products" : "/account/wishlist";

        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='margin:0;padding:0;background:#f9f9f9;font-family:Arial,sans-serif;'>");
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='background:#f9f9f9;padding:40px 0;'><tr><td align='center'>");
        sb.append("<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border:1px solid #e8e8e8;'>");

        // Header
        sb.append("<tr><td style='background:#000000;padding:32px 40px;'>");
        sb.append("<h1 style='margin:0;color:#ffffff;font-size:24px;font-weight:900;letter-spacing:4px;'>EGO</h1>");
        sb.append("</td></tr>");

        // Colour-coded status banner
        sb.append("<tr><td style='background:").append(bannerColor).append(";padding:10px 40px;'>");
        sb.append("<p style='margin:0;color:#ffffff;font-size:11px;font-weight:700;letter-spacing:2px;'>").append(badgeLabel).append("</p>");
        sb.append("</td></tr>");

        // Body
        sb.append("<tr><td style='padding:48px 40px;'>");
        sb.append("<h2 style='margin:0 0 8px 0;font-size:20px;font-weight:800;color:#111;'>").append(headline).append("</h2>");
        sb.append("<p style='margin:0 0 24px 0;color:#555;font-size:15px;'>Hi ").append(escapeHtml(firstName)).append(",</p>");
        sb.append("<p style='margin:0 0 32px 0;color:#555;font-size:15px;line-height:1.6;'>").append(bodyText).append("</p>");

        // Product pill
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #e8e8e8;margin-bottom:32px;'><tr><td style='padding:16px 20px;'>");
        sb.append("<p style='margin:0;font-size:14px;font-weight:700;color:#111;'>").append(escapeHtml(productName)).append("</p>");
        sb.append("<p style='margin:4px 0 0 0;font-size:12px;color:#888;'>Your Wishlist Item</p>");
        sb.append("</td></tr></table>");

        // CTA button
        sb.append("<table cellpadding='0' cellspacing='0' style='margin-bottom:24px;'><tr><td style='background:#000;padding:16px 40px;'>");
        sb.append("<a href='").append(ctaPath).append("' style='color:#fff;font-size:13px;font-weight:700;letter-spacing:2px;text-transform:uppercase;text-decoration:none;'>").append(ctaLabel).append("</a>");
        sb.append("</td></tr></table>");

        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style='background:#f9f9f9;padding:24px 40px;border-top:1px solid #e8e8e8;'>");
        sb.append("<p style='margin:0;font-size:12px;color:#aaa;text-align:center;'>EGO Fashion · You received this because the item is in your wishlist</p>");
        sb.append("</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }
}
