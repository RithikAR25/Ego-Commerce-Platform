package com.ego.raw_ego.notification.listener;

import com.ego.raw_ego.notification.event.OrderDeliveredEvent;
import com.ego.raw_ego.notification.event.OrderPlacedEvent;
import com.ego.raw_ego.notification.event.OrderShippedEvent;
import com.ego.raw_ego.notification.event.PaymentConfirmedEvent;
import com.ego.raw_ego.notification.event.RefundCompletedEvent;
import com.ego.raw_ego.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Spring event listener that dispatches transactional email notifications
 * in response to commerce lifecycle events.
 *
 * <h3>Async design (CRITICAL — read before modifying)</h3>
 * <p>All listener methods are annotated {@code @Async}, which means Spring
 * dispatches them on the configured async thread pool
 * ({@code ego-async-*} threads, configured via {@code spring.task.execution.*}).
 *
 * <p>This design ensures:
 * <ol>
 *   <li>The publishing transaction ({@code OrderService.checkout()} or
 *       {@code PaymentService.confirmOrder()}) completes and commits FIRST.</li>
 *   <li>Email dispatch is completely decoupled from the HTTP request thread —
 *       a slow or failed SendGrid call never affects the order response latency.</li>
 *   <li>If SendGrid throws or returns an error, the listener catches it internally
 *       via {@link NotificationService} — it never propagates out to Spring's
 *       uncaught exception handler.</li>
 * </ol>
 *
 * <h3>Ordering guarantee</h3>
 * <p>The events are published AFTER {@code saveAndFlush()} in the service layer,
 * ensuring the order data is committed to the database before the listener reads it.
 * The {@code @Async} listener opens its own Hibernate session for DB reads.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    /**
     * Handles the order-placed event — sends order confirmation email.
     *
     * <p>Fires after {@code OrderService.checkout()} commits the order with
     * {@code PENDING_PAYMENT} status.
     *
     * @param event carries orderId and userId
     */
    @Async
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("[NotificationListener] ORDER_PLACED received: orderId={} userId={}",
                event.getOrderId(), event.getUserId());
        try {
            notificationService.sendOrderConfirmation(event.getOrderId());
        } catch (Exception e) {
            // Should never reach here — NotificationService catches all exceptions internally.
            // Belt-and-suspenders guard so async thread does not die silently.
            log.error("[NotificationListener] Unhandled error in onOrderPlaced: orderId={} error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handles the payment-confirmed event — sends payment success email.
     *
     * <p>Fires after {@code PaymentService.confirmOrder()} commits the
     * {@code CONFIRMED} status and {@code razorpay_payment_id}.
     *
     * @param event carries orderId and userId
     */
    @Async
    @EventListener
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        log.info("[NotificationListener] PAYMENT_CONFIRMED received: orderId={} userId={}",
                event.getOrderId(), event.getUserId());
        try {
            notificationService.sendPaymentConfirmation(event.getOrderId());
        } catch (Exception e) {
            log.error("[NotificationListener] Unhandled error in onPaymentConfirmed: orderId={} error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handles the order-shipped event — sends shipping confirmation email.
     *
     * <p>Fires after an admin advances the order to {@code SHIPPED} status
     * via {@code PUT /api/v1/admin/orders/{id}/status}.
     *
     * @param event carries orderId and userId
     */
    @Async
    @EventListener
    public void onOrderShipped(OrderShippedEvent event) {
        log.info("[NotificationListener] ORDER_SHIPPED received: orderId={} userId={}",
                event.getOrderId(), event.getUserId());
        try {
            notificationService.sendOrderShipped(event.getOrderId());
        } catch (Exception e) {
            log.error("[NotificationListener] Unhandled error in onOrderShipped: orderId={} error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handles the order-delivered event — sends delivery confirmation email.
     *
     * <p>Fires after an admin advances the order to {@code DELIVERED} status.
     * The email includes a review CTA and a reminder of the 7-day return window.
     *
     * @param event carries orderId and userId
     */
    @Async
    @EventListener
    public void onOrderDelivered(OrderDeliveredEvent event) {
        log.info("[NotificationListener] ORDER_DELIVERED received: orderId={} userId={}",
                event.getOrderId(), event.getUserId());
        try {
            notificationService.sendOrderDelivered(event.getOrderId());
        } catch (Exception e) {
            log.error("[NotificationListener] Unhandled error in onOrderDelivered: orderId={} error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handles the refund-completed event — sends refund confirmation email.
     *
     * <p>Fires after {@code ReturnService.completeRefundAndUpdateOrder()} commits
     * the return to {@code REFUND_COMPLETED} and the order to {@code REFUNDED}.
     * The email includes the Razorpay refund ID and expected credit timeline.
     *
     * @param event carries orderId, userId, and razorpayRefundId
     */
    @Async
    @EventListener
    public void onRefundCompleted(RefundCompletedEvent event) {
        log.info("[NotificationListener] REFUND_COMPLETED received: orderId={} userId={} refundId={}",
                event.getOrderId(), event.getUserId(), event.getRazorpayRefundId());
        try {
            notificationService.sendRefundCompleted(event.getOrderId(), event.getRazorpayRefundId());
        } catch (Exception e) {
            log.error("[NotificationListener] Unhandled error in onRefundCompleted: orderId={} error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}

