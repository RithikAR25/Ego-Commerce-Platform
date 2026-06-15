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
 * Implementation of {@link NotificationLogService}.
 * Uses {@code REQUIRES_NEW} to ensure log writes always commit independently.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationLogServiceImpl implements NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(Long orderId, String recipientEmail,
                           NotificationEventType eventType, String messageId) {
        NotificationLog logEntry = NotificationLog.builder()
                .orderId(orderId)
                .recipientEmail(recipientEmail)
                .eventType(eventType)
                .status(NotificationStatus.SUCCESS)
                .messageId(messageId)
                .build();
        notificationLogRepository.save(logEntry);
        log.info("[NotificationLog] SUCCESS: orderId={} eventType={} messageId={}", orderId, eventType, messageId);
    }

    @Override
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
        log.warn("[NotificationLog] FAILED: orderId={} eventType={} error={}", orderId, eventType, errorMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean alreadySent(Long orderId, NotificationEventType eventType) {
        return notificationLogRepository
                .existsByOrderIdAndEventTypeAndStatus(orderId, eventType, NotificationStatus.SUCCESS);
    }
}
