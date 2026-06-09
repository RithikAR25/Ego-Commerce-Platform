package com.ego.raw_ego.notification.entity;

import com.ego.raw_ego.notification.enums.NotificationEventType;
import com.ego.raw_ego.notification.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity mapping to the {@code notification_logs} table.
 *
 * <p>Every email dispatch attempt — successful or failed — is persisted here.
 * This provides a full audit trail and a data source for future retry logic.
 *
 * <h3>Key design decisions</h3>
 * <ul>
 *   <li>{@code orderId} is a plain {@code Long} (not a FK) — decouples the
 *       notification log from the order lifecycle. Logs outlive order deletes.</li>
 *   <li>{@code status = FAILED} rows are written on exceptions so email errors
 *       are visible without rolling back the parent commerce transaction.</li>
 *   <li>{@code messageId} captures the {@code X-Message-Id} header returned by
 *       SendGrid — can be used to trace delivery status in the SendGrid dashboard.</li>
 * </ul>
 */
@Entity
@Table(
        name = "notification_logs",
        indexes = {
                @Index(name = "idx_notification_logs_order_id",   columnList = "order_id"),
                @Index(name = "idx_notification_logs_event_type", columnList = "event_type"),
                @Index(name = "idx_notification_logs_status",     columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The order this notification is associated with.
     * Nullable — future notification types may not be order-scoped.
     */
    @Column(name = "order_id")
    private Long orderId;

    /** Email address the notification was sent to. */
    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    /** Which event triggered this notification. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private NotificationEventType eventType;

    /** Outcome of the dispatch attempt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status;

    /**
     * Error message from SendGrid or local exception.
     * Null on SUCCESS. Populated on FAILED.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * The {@code X-Message-Id} response header from SendGrid.
     * Non-null on SUCCESS. Used to trace delivery in the SendGrid Activity Feed.
     */
    @Column(name = "message_id", length = 255)
    private String messageId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
