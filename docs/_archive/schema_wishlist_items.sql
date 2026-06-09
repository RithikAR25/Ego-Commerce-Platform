-- Phase 9B — Wishlist Schema
-- Run this against your EGO MySQL database to create the wishlist_items table.

CREATE TABLE IF NOT EXISTS wishlist_items (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    variant_id  BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_wishlist_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT fk_wishlist_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,

    CONSTRAINT uq_wishlist_user_variant
        UNIQUE (user_id, variant_id)
);

CREATE INDEX IF NOT EXISTS idx_wishlist_user ON wishlist_items (user_id);
