package com.ego.raw_ego.notification.service;

import com.ego.raw_ego.notification.entity.NotificationLog;
import com.ego.raw_ego.notification.enums.NotificationEventType;
import com.ego.raw_ego.notification.enums.NotificationStatus;
import com.ego.raw_ego.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists notification send-attempt records to the {@code notification_logs} table.
 *
 * <h3>Why {@code REQUIRES_NEW}?</h3>
 * <p>This service is called from {@code NotificationService}, which itself runs on
 * an async thread (from {@code @Async} in the event listener). However, the async
 * thread may not always have an active transaction. Using {@code REQUIRES_NEW} ensures
 * a fresh transaction is always opened and committed immediately for each log write —
 * regardless of whether the caller has a transaction or not.
 *
 * <p>This design means log writes survive even if the SendGrid call fails
 * halfway through, and the failure is durably recorded.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    /**
     * Persists a {@code SUCCESS} log entry for a delivered notification.
     *
     * @param orderId        the associated order ID (nullable for non-order notifications)
     * @param recipientEmail the email address that was sent to
     * @param eventType      which notification event this was
     * @param messageId      the SendGrid {@code X-Message-Id} response header value
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(Long orderId, String recipientEmail,
                           NotificationEventType eventType, String messageId) {
        NotificationLog log = NotificationLog.builder()
                .orderId(orderId)
                .recipientEmail(recipientEmail)
                .eventType(eventType)
                .status(NotificationStatus.SUCCESS)
                .messageId(messageId)
                .build();
        notificationLogRepository.save(log);
        this.log.info("[NotificationLog] SUCCESS: orderId={} eventType={} messageId={}",
                orderId, eventType, messageId);
    }

    /**
     * Persists a {@code FAILED} log entry when a notification could not be delivered.
     *
     * <p>A {@code FAILED} row is always written — even on exceptions — so the
     * failure is durably recorded for debugging and potential retry logic.
     *
     * @param orderId        the associated order ID (nullable)
     * @param recipientEmail the intended recipient
     * @param eventType      which notification event was attempted
     * @param errorMessage   the error detail (SendGrid status body or exception message)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(Long orderId, String recipientEmail,
                           NotificationEventType eventType, String errorMessage) {
        NotificationLog failLog = NotificationLog.builder()
                .orderId(orderId)
                .recipientEmail(recipientEmail)
                .eventType(eventType)
                .status(NotificationStatus.FAILED)
                .errorMessage(errorMessage)
                .build();
        notificationLogRepository.save(failLog);
        this.log.warn("[NotificationLog] FAILED: orderId={} eventType={} error={}",
                orderId, eventType, errorMessage);
    }

    /**
     * Idempotency check — returns true if a SUCCESS log already exists
     * for the given (orderId, eventType) pair.
     *
     * <p>Prevents duplicate emails when the same event fires more than once
     * (e.g. duplicate Razorpay webhook deliveries).
     *
     * @param orderId   the EGO order ID
     * @param eventType the notification type to check
     * @return true if this notification was already successfully sent
     */
    @Transactional(readOnly = true)
    public boolean alreadySent(Long orderId, NotificationEventType eventType) {
        return notificationLogRepository
                .existsByOrderIdAndEventTypeAndStatus(orderId, eventType, NotificationStatus.SUCCESS);
    }
}
