package com.ego.raw_ego.notification.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired after a return request is approved and the
 * Razorpay refund is successfully processed (return status → {@code REFUND_COMPLETED}).
 *
 * <p>Published by {@code ReturnService.completeRefundAndUpdateOrder()} after both
 * the return request and order status updates are committed to the database.
 *
 * <p>Consumed asynchronously by {@code NotificationEventListener.onRefundCompleted()},
 * which dispatches the refund confirmation email on the {@code ego-async-*} thread pool.
 * The email includes the Razorpay refund ID and the expected credit timeline.
 *
 * <p>Unlike order events, this event carries {@code razorpayRefundId} so the
 * email can show the customer a reference ID for their bank records.
 */
public class RefundCompletedEvent extends ApplicationEvent {

    private final Long   orderId;
    private final Long   userId;
    private final String razorpayRefundId;

    public RefundCompletedEvent(Object source, Long orderId, Long userId, String razorpayRefundId) {
        super(source);
        this.orderId          = orderId;
        this.userId           = userId;
        this.razorpayRefundId = razorpayRefundId;
    }

    public Long   getOrderId()          { return orderId;          }
    public Long   getUserId()           { return userId;           }
    public String getRazorpayRefundId() { return razorpayRefundId; }
}
