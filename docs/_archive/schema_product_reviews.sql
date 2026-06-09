-- Phase 9A — Product Reviews Schema
-- Run this against your EGO MySQL database to create the product_reviews table.

CREATE TABLE IF NOT EXISTS product_reviews (
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    product_id           BIGINT       NOT NULL,
    user_id              BIGINT       NOT NULL,
    reviewer_first_name  VARCHAR(100) NOT NULL,
    rating               TINYINT      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title                VARCHAR(150),
    body                 TEXT,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                             ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_review_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,

    CONSTRAINT fk_review_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT uq_user_product_review
        UNIQUE (user_id, product_id)
);

CREATE INDEX idx_review_product ON product_reviews (product_id);
CREATE INDEX idx_review_user ON product_reviews (user_id);
