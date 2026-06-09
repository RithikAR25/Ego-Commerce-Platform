package com.ego.raw_ego.notification.enums;

/**
 * Outcome of a single notification dispatch attempt.
 *
 * <p>Stored in {@code notification_logs.status}.
 * A {@code FAILED} row is logged (not thrown) so email errors never
 * roll back the parent order or payment transaction.
 */
public enum NotificationStatus {

    /** SendGrid accepted the email (2xx response). */
    SUCCESS,

    /** SendGrid returned an error, or a local exception occurred before sending. */
    FAILED
}
