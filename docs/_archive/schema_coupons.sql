-- Phase 9C — Coupon Codes Schema
-- Run this against your EGO MySQL database.

-- ── coupons ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupons (
    id                  BIGINT        AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(50)   NOT NULL UNIQUE,
    description         VARCHAR(255),
    discount_type       ENUM('FLAT','PERCENTAGE') NOT NULL,
    discount_value      DECIMAL(10,2) NOT NULL,
    max_discount_amount DECIMAL(10,2),               -- cap for PERCENTAGE (NULL = no cap)
    min_order_amount    DECIMAL(10,2),               -- min subtotal required (NULL = none)
    max_uses            INT,                         -- NULL = unlimited uses
    current_uses        INT           NOT NULL DEFAULT 0,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    expires_at          DATETIME(6),                 -- NULL = never expires
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                            ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE INDEX IF NOT EXISTS idx_coupon_code ON coupons (code);

-- ── order_coupons (audit trail) ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_coupons (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT        NOT NULL UNIQUE,   -- one coupon per order
    coupon_id       BIGINT        NOT NULL,
    coupon_code     VARCHAR(50)   NOT NULL,           -- snapshot at apply time
    discount_amount DECIMAL(12,2) NOT NULL,           -- actual rupee discount applied
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_order_coupon_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,

    CONSTRAINT fk_order_coupon_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE RESTRICT
);

-- ── orders table additions ─────────────────────────────────────────────────────
-- Adds discount snapshot columns to orders.
-- Safe to run on existing tables (uses IF NOT EXISTS / column existence guard).

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS coupon_code_snapshot VARCHAR(50)   NULL
        COMMENT 'Snapshot of coupon code applied at checkout. NULL if none.',
    ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00
        COMMENT 'Rupee discount applied via coupon. 0.00 if no coupon.';
