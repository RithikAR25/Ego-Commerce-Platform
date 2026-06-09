package com.ego.raw_ego.notification.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired after a Razorpay webhook confirms payment
 * and the EGO order advances to {@code CONFIRMED}.
 *
 * <p>Published by {@code PaymentService.confirmOrder()} immediately after
 * {@code orderRepository.saveAndFlush()} — guaranteeing the CONFIRMED status
 * and {@code razorpay_payment_id} are committed before the async listener reads them.
 *
 * <p>Consumed asynchronously by {@code NotificationEventListener.onPaymentConfirmed()},
 * which dispatches the payment confirmation email on a separate thread pool.
 */
public class PaymentConfirmedEvent extends ApplicationEvent {

    private final Long orderId;
    private final Long userId;

    public PaymentConfirmedEvent(Object source, Long orderId, Long userId) {
        super(source);
        this.orderId = orderId;
        this.userId  = userId;
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId()  { return userId;  }
}
