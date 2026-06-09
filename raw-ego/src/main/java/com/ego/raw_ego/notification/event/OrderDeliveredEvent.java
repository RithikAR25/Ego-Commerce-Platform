package com.ego.raw_ego.notification.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired after an admin advances an order to
 * {@code DELIVERED} status.
 *
 * <p>Published by {@code OrderService.adminUpdateStatus()} immediately after
 * {@code orderRepository.saveAndFlush()} — guaranteeing the updated status is
 * committed to the database before any async listener reads it.
 *
 * <p>Consumed asynchronously by {@code NotificationEventListener.onOrderDelivered()},
 * which dispatches the delivery confirmation email on the {@code ego-async-*} thread pool.
 * The email includes a CTA to leave a review and a reminder of the 7-day return window.
 */
public class OrderDeliveredEvent extends ApplicationEvent {

    private final Long orderId;
    private final Long userId;

    public OrderDeliveredEvent(Object source, Long orderId, Long userId) {
        super(source);
        this.orderId = orderId;
        this.userId  = userId;
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId()  { return userId;  }
}
