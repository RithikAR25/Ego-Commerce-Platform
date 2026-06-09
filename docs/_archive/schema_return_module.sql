-- ============================================================
--  Phase 10 — Return & Refund Module
--  Schema additions for the `rawego` database
--  Run once on the development database.
--  JPA ddl-auto=update will handle dev auto-creation,
--  but this script is the authoritative reference for production migrations.
-- ============================================================

-- ------------------------------------------------------------
-- Table: return_requests
-- One row per customer return request. One non-rejected return
-- allowed per order (enforced at service level).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS return_requests (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,

    -- FK to the order being returned (must be DELIVERED)
    order_id            BIGINT          NOT NULL,

    -- FK to the customer who submitted the return
    requested_by        BIGINT          NOT NULL,

    -- Customer-selected reason category
    -- Enum values: DEFECTIVE, WRONG_ITEM, SIZE_ISSUE, NOT_AS_DESCRIBED, OTHER
    reason              VARCHAR(30)     NOT NULL,

    -- Optional free-text from the customer
    reason_detail       TEXT,

    -- Current return state machine status
    -- Enum values: REQUESTED, APPROVED, REJECTED, REFUND_INITIATED, REFUND_COMPLETED
    status              VARCHAR(30)     NOT NULL DEFAULT 'REQUESTED',

    -- Rupee refund amount set by admin at approval time (nullable until approved)
    -- Supports partial refunds (admin sets any amount ≤ order.grand_total)
    refund_amount       DECIMAL(12, 2),

    -- Razorpay refund ID returned by razorpay.payments.refund() API
    -- Format: rfnd_XXXXXXXXXXXXXXXXXX
    -- Null until the Razorpay refund call completes
    razorpay_refund_id  VARCHAR(100),

    -- Optional admin notes (visible to customer on status check)
    admin_notes         TEXT,

    -- Optimistic lock version — prevents concurrent admin review races
    version             INT             NOT NULL DEFAULT 1,

    created_at          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                            ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_return_requests_order
        FOREIGN KEY (order_id) REFERENCES orders (id),

    CONSTRAINT fk_return_requests_user
        FOREIGN KEY (requested_by) REFERENCES users (id),

    -- Fast lookup: find return requests by order (ReturnRequestRepository)
    INDEX idx_return_requests_order_id  (order_id),

    -- Fast lookup: admin filter by status (findAllByStatusFilter paginated query)
    INDEX idx_return_requests_status    (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============================================================
-- Verification queries (run after table creation)
-- ============================================================
-- DESCRIBE return_requests;
-- SHOW INDEX FROM return_requests;
-- SHOW CREATE TABLE return_requests\G
