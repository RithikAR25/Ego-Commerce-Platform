-- ============================================================
-- Schema: notification_logs
-- Phase 8 — SendGrid Transactional Email Notifications
-- Run against: rawego database
-- ============================================================
-- Note: With JPA ddl-auto=update, Hibernate will auto-create
-- this table from the NotificationLog entity. This script is
-- provided for manual execution in production environments
-- where ddl-auto=validate or ddl-auto=none is used.
-- ============================================================

CREATE TABLE IF NOT EXISTS notification_logs (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    order_id        BIGINT UNSIGNED     NULL     COMMENT 'Associated EGO order — nullable for non-order notifications',
    recipient_email VARCHAR(254)        NOT NULL COMMENT 'Email address the notification was sent to',
    event_type      VARCHAR(30)         NOT NULL COMMENT 'Trigger event: ORDER_PLACED | PAYMENT_CONFIRMED',
    status          VARCHAR(10)         NOT NULL COMMENT 'Dispatch outcome: SUCCESS | FAILED',
    error_message   TEXT                NULL     COMMENT 'SendGrid error body or exception message — null on SUCCESS',
    message_id      VARCHAR(255)        NULL     COMMENT 'SendGrid X-Message-Id response header — null on FAILED',
    created_at      TIMESTAMP(6)        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    -- Fast lookup by order for admin views and idempotency checks
    INDEX idx_notification_logs_order_id   (order_id),

    -- Analytics: count notifications by event type
    INDEX idx_notification_logs_event_type (event_type),

    -- Monitoring: find all failed sends for retry logic
    INDEX idx_notification_logs_status     (status)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Audit log for all transactional email dispatch attempts (Phase 8)';

-- ============================================================
-- Verification query — run after table creation:
-- ============================================================
-- DESCRIBE notification_logs;
-- SELECT COUNT(*) FROM notification_logs;
--
-- After a test checkout:
-- SELECT id, order_id, recipient_email, event_type, status, message_id, created_at
-- FROM notification_logs
-- ORDER BY created_at DESC
-- LIMIT 10;
