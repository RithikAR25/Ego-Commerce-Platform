package com.ego.raw_ego.notification.service;

import com.ego.raw_ego.notification.enums.NotificationEventType;

/**
 * Contract for persisting notification send-attempt records to {@code notification_logs}.
 *
 * <h3>Why {@code REQUIRES_NEW}?</h3>
 * <p>Called from {@code NotificationService} on an async thread. Using {@code REQUIRES_NEW}
 * ensures a fresh transaction is always opened and committed immediately for each log write —
 * regardless of whether the caller has a transaction or not. This means log writes survive
 * even if the SendGrid call fails halfway through.
 */
public interface NotificationLogService {

    /**
     * Persists a {@code SUCCESS} log entry for a delivered notification.
     */
    void logSuccess(Long orderId, String recipientEmail, NotificationEventType eventType, String messageId);

    /**
     * Persists a {@code FAILED} log entry when a notification could not be delivered.
     */
    void logFailure(Long orderId, String recipientEmail, NotificationEventType eventType, String errorMessage);

    /**
     * Idempotency check — returns true if a SUCCESS log already exists
     * for the given (orderId, eventType) pair.
     */
    boolean alreadySent(Long orderId, NotificationEventType eventType);
}
