package com.ego.raw_ego.notification.repository;

import com.ego.raw_ego.notification.entity.NotificationLog;
import com.ego.raw_ego.notification.enums.NotificationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link NotificationLog}.
 *
 * <p>Used by {@code NotificationLogService} to persist send attempts
 * and by {@code NotificationService} to check idempotency before sending.
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * Idempotency guard — checks whether a notification of a given type
     * was already successfully sent for an order.
     *
     * <p>Called before each send attempt to prevent duplicate emails when
     * the same event fires more than once (e.g. duplicate Razorpay webhooks).
     *
     * @param orderId   the EGO order ID
     * @param eventType the notification event type
     * @return true if a SUCCESS log already exists for this (orderId, eventType) pair
     */
    boolean existsByOrderIdAndEventTypeAndStatus(
            Long orderId,
            NotificationEventType eventType,
            com.ego.raw_ego.notification.enums.NotificationStatus status
    );
}
