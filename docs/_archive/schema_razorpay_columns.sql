-- =============================================================================
-- EGO Platform — Razorpay Columns Migration
-- Phase 7: Razorpay Payment Integration
-- Applied: May 2026
-- Target: rawego schema
--
-- Adds razorpay_order_id and razorpay_payment_id to the orders table.
--
-- NOTE: Spring JPA (ddl-auto=update) will add these columns automatically
-- on next server start. Run this script ONLY if you want to apply the
-- schema change manually before restarting the server.
-- =============================================================================

ALTER TABLE orders
    ADD COLUMN razorpay_order_id   VARCHAR(100) DEFAULT NULL AFTER shipping_address,
    ADD COLUMN razorpay_payment_id VARCHAR(100) DEFAULT NULL AFTER razorpay_order_id;

-- Index for webhook lookup: find EGO order by razorpay_order_id
CREATE INDEX idx_orders_razorpay_order_id ON orders (razorpay_order_id);

-- =============================================================================
-- Verification:
-- DESCRIBE orders;
-- Expected new columns: razorpay_order_id, razorpay_payment_id (both nullable VARCHAR 100)
-- =============================================================================
