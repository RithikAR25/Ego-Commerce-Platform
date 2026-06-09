package com.ego.raw_ego.notification.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired after a customer successfully places an order
 * (status: {@code PENDING_PAYMENT}).
 *
 * <p>Published by {@code OrderService.checkout()} immediately after
 * {@code orderRepository.saveAndFlush()} — guaranteeing the order is
 * committed to the database before any async listener reads it.
 *
 * <p>Consumed asynchronously by {@code NotificationEventListener.onOrderPlaced()},
 * which dispatches the order confirmation email on a separate thread pool.
 * The listener never blocks or rolls back the caller's transaction.
 */
public class OrderPlacedEvent extends ApplicationEvent {

    private final Long orderId;
    private final Long userId;

    public OrderPlacedEvent(Object source, Long orderId, Long userId) {
        super(source);
        this.orderId = orderId;
        this.userId  = userId;
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId()  { return userId;  }
}
