-- =============================================================================
--  Production-Grade MySQL Schema  v2.0
--  Clothing E-Commerce Platform
--  Engine: InnoDB | Charset: utf8mb4 | Collation: utf8mb4_unicode_ci
-- =============================================================================
--  Changes from v1:
--    + audit_logs                     (new — cross-cutting immutable event log)
--    + order_address_snapshots        (new — immutable delivery address copy)
--    + stock_reservations             (new — durable inventory lock with TTL)
--    + refunds                        (new — Razorpay refund records, partial support)
--    + review_media                   (new — photo/video on reviews)
--    ~ products          + avg_rating, review_count, compare_price, gender, is_featured
--    ~ product_images    + cloudinary_id replaces full url VARCHAR
--    ~ product_attribute_values  + swatch_image_url
--    ~ product_variants  + weight_grams, updated_at
--    ~ addresses         + label, updated_at
--    ~ categories        + is_deleted, updated_at
--    ~ coupons           + max_discount_cap, is_deleted, scoped applicability, user_id on usages
--    ~ orders            + subtotal, shipping_charge, tax_amount, payment_failed status
--    ~ order_items       + product_name, sku, variant_label snapshots
--    ~ order_status_history  + from_status column
--    ~ reviews           + order_id FK; unique corrected to (user, order, product)
--    ~ wishlist_items    + product_id FK; variant_id made nullable
--    ~ inventory_records + updated_at
--    ~ return_requests   + updated_at, admin_notes, pickup_scheduled status
--    ~ notification_log  + recipient_email, reference_type/id, bounced status
-- =============================================================================

SET NAMES utf8mb4;
SET foreign_key_checks = 0;


-- =============================================================================
--  0. AUDIT LOG  (immutable — never UPDATE or DELETE rows here)
-- =============================================================================

CREATE TABLE audit_logs (
    id           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    actor_id     BIGINT UNSIGNED  DEFAULT NULL,               -- NULL = system/scheduler
    actor_role   ENUM('customer','admin','system')
                                  NOT NULL DEFAULT 'system',
    action       VARCHAR(100)     NOT NULL,                   -- 'ORDER_STATUS_CHANGED'
    entity_type  VARCHAR(100)     NOT NULL,                   -- 'orders'
    entity_id    BIGINT UNSIGNED  NOT NULL,
    old_value    JSON             DEFAULT NULL,
    new_value    JSON             DEFAULT NULL,
    ip_address   VARCHAR(45)      DEFAULT NULL,               -- IPv4 or IPv6
    created_at   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY  (id),
    INDEX idx_audit_entity  (entity_type, entity_id),
    INDEX idx_audit_actor   (actor_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only. No UPDATE or DELETE ever.';


-- =============================================================================
--  1. USERS & AUTH
-- =============================================================================

CREATE TABLE users (
    id                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    email               VARCHAR(254)     NOT NULL,
    password_hash       VARCHAR(255)     NOT NULL,            -- BCrypt strength 12; never raw
    phone               VARCHAR(20)      DEFAULT NULL,        -- E.164 format recommended
    first_name          VARCHAR(100)     NOT NULL,
    last_name           VARCHAR(100)     NOT NULL,
    role                ENUM('customer','admin')
                                         NOT NULL DEFAULT 'customer',
    is_active           TINYINT(1)       NOT NULL DEFAULT 1,
    is_email_verified   TINYINT(1)       NOT NULL DEFAULT 0,
    is_deleted          TINYINT(1)       NOT NULL DEFAULT 0,
    password_changed_at DATETIME(3)      DEFAULT NULL,        -- invalidates JWTs issued before
    last_login_at       DATETIME(3)      DEFAULT NULL,
    version             INT UNSIGNED     NOT NULL DEFAULT 1,  -- optimistic lock
    created_at          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                         ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_users_email       (email),
    INDEX       idx_users_phone      (phone),
    INDEX       idx_users_role       (role, is_active, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Refresh tokens — persisted so logout truly revokes.
-- family_id enables token rotation: reuse of any family member revokes the whole family.
CREATE TABLE refresh_tokens (
    id          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED  NOT NULL,
    token_hash  VARCHAR(255)     NOT NULL,                    -- SHA-256 of raw token
    family_id   CHAR(36)         NOT NULL,                    -- UUID; rotate entire family on reuse
    device_hint VARCHAR(255)     DEFAULT NULL,                -- 'Chrome / macOS'
    ip_address  VARCHAR(45)      DEFAULT NULL,
    expires_at  DATETIME(3)      NOT NULL,
    revoked_at  DATETIME(3)      DEFAULT NULL,                -- NULL = still valid
    created_at  DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_refresh_hash    (token_hash),
    INDEX       idx_refresh_user   (user_id, expires_at),
    INDEX       idx_refresh_family (family_id),
    CONSTRAINT fk_refresh_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Addresses — mutable by user; orders snapshot them in order_address_snapshots.
CREATE TABLE addresses (
    id           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED  NOT NULL,
    label        VARCHAR(50)      NOT NULL DEFAULT 'Home',    -- 'Home', 'Work', 'Other'
    full_name    VARCHAR(200)     NOT NULL,
    phone        VARCHAR(20)      DEFAULT NULL,
    line1        VARCHAR(255)     NOT NULL,
    line2        VARCHAR(255)     DEFAULT NULL,
    city         VARCHAR(100)     NOT NULL,
    state        VARCHAR(100)     NOT NULL,
    postal_code  VARCHAR(20)      NOT NULL,
    country_code CHAR(2)          NOT NULL DEFAULT 'IN',      -- ISO 3166-1 alpha-2
    is_default   TINYINT(1)       NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)       NOT NULL DEFAULT 0,
    created_at   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                   ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_addr_user (user_id, is_deleted),
    CONSTRAINT fk_addr_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  2. CATALOGUE
-- =============================================================================

CREATE TABLE categories (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT UNSIGNED   DEFAULT NULL,             -- self-referential tree
    name          VARCHAR(150)      NOT NULL,
    slug          VARCHAR(160)      NOT NULL,
    description   TEXT              DEFAULT NULL,
    image_url     VARCHAR(500)      DEFAULT NULL,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    is_active     TINYINT(1)        NOT NULL DEFAULT 1,
    is_deleted    TINYINT(1)        NOT NULL DEFAULT 0,
    created_at    DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_category_slug  (slug),
    INDEX       idx_cat_parent    (parent_id),
    INDEX       idx_cat_active    (is_active, is_deleted),
    CONSTRAINT fk_cat_parent
        FOREIGN KEY (parent_id) REFERENCES categories (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- avg_rating + review_count: denormalized caches updated on every review write.
-- Without them every product listing fires a COUNT + AVG subquery against reviews.
-- compare_price: "was ₹999" strikethrough value; NULL = no active sale.
CREATE TABLE products (
    id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    category_id       BIGINT UNSIGNED  NOT NULL,
    slug              VARCHAR(200)     NOT NULL,
    name              VARCHAR(255)     NOT NULL,
    description       TEXT             DEFAULT NULL,
    care_instructions TEXT             DEFAULT NULL,
    material          VARCHAR(255)     DEFAULT NULL,
    base_price        DECIMAL(12,2)    NOT NULL CHECK (base_price >= 0),
    compare_price     DECIMAL(12,2)    DEFAULT NULL,
    gender            ENUM('men','women','unisex','kids')
                                       NOT NULL DEFAULT 'unisex',
    avg_rating        DECIMAL(3,2)     NOT NULL DEFAULT 0.00
                                       CHECK (avg_rating BETWEEN 0.00 AND 5.00),
    review_count      INT UNSIGNED     NOT NULL DEFAULT 0,
    is_active         TINYINT(1)       NOT NULL DEFAULT 1,
    is_featured       TINYINT(1)       NOT NULL DEFAULT 0,
    is_deleted        TINYINT(1)       NOT NULL DEFAULT 0,
    version           INT UNSIGNED     NOT NULL DEFAULT 1,
    created_at        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                       ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_product_slug         (slug),
    INDEX       idx_product_category    (category_id, is_active, is_deleted),
    INDEX       idx_product_gender      (gender, is_active, is_deleted),
    INDEX       idx_product_featured    (is_featured, is_active, is_deleted),
    INDEX       idx_product_price       (base_price, is_active, is_deleted),
    INDEX       idx_product_rating      (avg_rating DESC),
    FULLTEXT INDEX ft_product_search    (name, description),
    CONSTRAINT chk_compare_price CHECK (compare_price IS NULL OR compare_price >= base_price),
    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES categories (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- EAV attribute system: "Color", "Size", "Fit" …
-- New attribute axis = INSERT row, no ALTER TABLE required.
CREATE TABLE product_attribute_types (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    product_id    BIGINT UNSIGNED   NOT NULL,
    name          VARCHAR(100)      NOT NULL,                 -- 'size', 'color'
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uq_attr_type_product_name (product_id, name),
    CONSTRAINT fk_attr_type_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE product_attribute_values (
    id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    attribute_type_id BIGINT UNSIGNED   NOT NULL,
    value             VARCHAR(200)      NOT NULL,             -- 'S', 'M', 'Red', 'Black'
    hex_color         CHAR(7)           DEFAULT NULL,         -- '#FF0000' for color swatches
    swatch_image_url  VARCHAR(500)      DEFAULT NULL,         -- for print / pattern swatches
    display_order     SMALLINT UNSIGNED NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY  uq_attr_val_type_value (attribute_type_id, value),
    INDEX       idx_attr_val_type      (attribute_type_id),
    CONSTRAINT fk_attr_val_type
        FOREIGN KEY (attribute_type_id) REFERENCES product_attribute_types (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- One row per purchasable SKU: "Acid-Wash Tee / Red / M"
CREATE TABLE product_variants (
    id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    product_id     BIGINT UNSIGNED  NOT NULL,
    sku            VARCHAR(100)     NOT NULL,
    price_override DECIMAL(12,2)    DEFAULT NULL
                                    CHECK (price_override IS NULL OR price_override >= 0),
    weight_grams   SMALLINT UNSIGNED DEFAULT NULL,            -- for shipping rate calc
    is_active      TINYINT(1)       NOT NULL DEFAULT 1,
    created_at     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_variant_sku      (sku),
    INDEX       idx_variant_product (product_id, is_active),
    CONSTRAINT fk_variant_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Junction: which attribute values compose a variant (e.g. Red + M)
CREATE TABLE variant_attribute_values (
    variant_id         BIGINT UNSIGNED NOT NULL,
    attribute_value_id BIGINT UNSIGNED NOT NULL,

    PRIMARY KEY (variant_id, attribute_value_id),
    INDEX idx_vav_attr_val (attribute_value_id),
    CONSTRAINT fk_vav_variant
        FOREIGN KEY (variant_id)        REFERENCES product_variants        (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_vav_attr_val
        FOREIGN KEY (attribute_value_id) REFERENCES product_attribute_values (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Store cloudinary_id only — build URLs at runtime via Cloudinary SDK.
-- Changing transformation params = one line of code, not millions of UPDATE rows.
CREATE TABLE product_images (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    product_id    BIGINT UNSIGNED   NOT NULL,
    variant_id    BIGINT UNSIGNED   DEFAULT NULL,             -- NULL = all variants
    cloudinary_id VARCHAR(500)      NOT NULL,                 -- e.g. 'products/42/abc123xyz'
    alt_text      VARCHAR(255)      DEFAULT NULL,
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    is_primary    TINYINT(1)        NOT NULL DEFAULT 0,
    created_at    DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_pimg_product (product_id, display_order),
    INDEX idx_pimg_variant (variant_id),
    CONSTRAINT fk_pimg_product
        FOREIGN KEY (product_id) REFERENCES products         (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_pimg_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  3. INVENTORY
-- =============================================================================
-- qty_available = total stock on hand
-- qty_reserved  = held by active checkout sessions (not yet committed)
-- sellable      = qty_available - qty_reserved
-- version       = compare-and-swap guard against concurrent oversell

CREATE TABLE inventory_records (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    variant_id          BIGINT UNSIGNED NOT NULL,
    qty_available       INT             NOT NULL DEFAULT 0 CHECK (qty_available >= 0),
    qty_reserved        INT             NOT NULL DEFAULT 0 CHECK (qty_reserved  >= 0),
    low_stock_threshold INT             NOT NULL DEFAULT 5,
    allow_backorder     TINYINT(1)      NOT NULL DEFAULT 0,
    version             INT UNSIGNED    NOT NULL DEFAULT 1,   -- JPA @Version field
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uq_inv_variant (variant_id),
    CONSTRAINT fk_inv_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Durable reservation log. Redis is the hot lock; this is the recovery safety net.
-- Scheduled job runs every 60s:
--   WHERE expires_at < NOW() AND released_at IS NULL
--   → decrement qty_reserved, set released_at = NOW()
CREATE TABLE stock_reservations (
    id           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    variant_id   BIGINT UNSIGNED  NOT NULL,
    order_id     BIGINT UNSIGNED  DEFAULT NULL,               -- set when order is placed
    session_ref  VARCHAR(100)     NOT NULL,                   -- cart UUID or order_number
    qty_reserved INT              NOT NULL CHECK (qty_reserved > 0),
    expires_at   DATETIME(3)      NOT NULL,                   -- default: NOW() + 10 min
    released_at  DATETIME(3)      DEFAULT NULL,               -- NULL = still active
    created_at   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_reservation_variant (variant_id, expires_at),
    INDEX idx_reservation_session (session_ref),
    INDEX idx_reservation_expiry  (expires_at, released_at),  -- scheduler cleanup scan
    CONSTRAINT fk_reservation_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  4. WISHLIST
-- =============================================================================
-- variant_id nullable: user can wishlist a product before choosing size/color.
-- Unique constraint covers (user, product, variant) — NULL variant is distinct.

CREATE TABLE wishlist_items (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,                      -- always required
    variant_id BIGINT UNSIGNED DEFAULT NULL,                  -- optional
    added_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_wishlist_user_variant (user_id, product_id, variant_id),
    INDEX       idx_wishlist_product     (product_id),
    INDEX       idx_wishlist_variant     (variant_id),
    CONSTRAINT fk_wl_user
        FOREIGN KEY (user_id)    REFERENCES users            (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_wl_product
        FOREIGN KEY (product_id) REFERENCES products         (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_wl_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  5. CART  (DB cart = durable fallback for Redis session cart)
-- =============================================================================
-- Redis is the hot path for reads. This table is synced on every add/remove
-- and loaded on login so the cart survives across devices and sessions.

CREATE TABLE cart_items (
    id         BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED   NOT NULL,
    variant_id BIGINT UNSIGNED   NOT NULL,
    quantity   SMALLINT UNSIGNED NOT NULL DEFAULT 1 CHECK (quantity > 0),
    added_at   DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                  ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_cart_user_variant (user_id, variant_id),
    INDEX       idx_cart_user        (user_id),
    CONSTRAINT fk_cart_user
        FOREIGN KEY (user_id)    REFERENCES users            (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_cart_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  6. COUPONS
-- =============================================================================

CREATE TABLE coupons (
    id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code              VARCHAR(64)       NOT NULL,
    description       VARCHAR(255)      DEFAULT NULL,
    discount_type     ENUM('percentage','fixed_amount','free_shipping')
                                        NOT NULL,
    discount_value    DECIMAL(12,2)     NOT NULL CHECK (discount_value > 0),
    max_discount_cap  DECIMAL(12,2)     DEFAULT NULL,          -- cap for %-type: "up to ₹500 off"
    min_order_value   DECIMAL(12,2)     DEFAULT NULL,
    max_uses          INT UNSIGNED      DEFAULT NULL,           -- NULL = unlimited
    max_uses_per_user TINYINT UNSIGNED  NOT NULL DEFAULT 1,
    used_count        INT UNSIGNED      NOT NULL DEFAULT 0,
    applicable_to     ENUM('all','category','product')
                                        NOT NULL DEFAULT 'all',
    applicable_id     BIGINT UNSIGNED   DEFAULT NULL,           -- category_id or product_id
    valid_from        DATETIME(3)       DEFAULT NULL,
    valid_until       DATETIME(3)       DEFAULT NULL,
    is_active         TINYINT(1)        NOT NULL DEFAULT 1,
    is_deleted        TINYINT(1)        NOT NULL DEFAULT 0,
    created_at        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_coupon_code    (code),
    INDEX       idx_coupon_active (is_active, is_deleted, valid_until),
    CONSTRAINT chk_coupon_dates CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE coupon_usages (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id   BIGINT UNSIGNED NOT NULL,
    coupon_id  BIGINT UNSIGNED NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,                       -- needed for per-user cap check
    applied_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_coupon_usage_order  (order_id),             -- one coupon per order
    INDEX       idx_coupon_usage_user  (coupon_id, user_id),   -- per-user cap check
    CONSTRAINT fk_cu_order
        FOREIGN KEY (order_id)  REFERENCES orders  (id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_cu_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_cu_user
        FOREIGN KEY (user_id)   REFERENCES users   (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  7. ORDERS
-- =============================================================================

CREATE TABLE orders (
    id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    order_number    VARCHAR(32)      NOT NULL,                 -- ORD-20250521-00001
    user_id         BIGINT UNSIGNED  NOT NULL,
    address_id      BIGINT UNSIGNED  NOT NULL,                 -- live FK; snapshot in order_address_snapshots
    coupon_id       BIGINT UNSIGNED  DEFAULT NULL,
    status          ENUM(
                        'pending_payment',
                        'payment_failed',
                        'confirmed',
                        'processing',
                        'shipped',
                        'out_for_delivery',
                        'delivered',
                        'cancelled',
                        'return_requested',
                        'return_approved',
                        'return_rejected',
                        'returned',
                        'refund_initiated',
                        'refund_completed'
                    )                NOT NULL DEFAULT 'pending_payment',
    subtotal        DECIMAL(12,2)    NOT NULL CHECK (subtotal >= 0),   -- sum of line items
    discount_amount DECIMAL(12,2)    NOT NULL DEFAULT 0.00,
    shipping_charge DECIMAL(12,2)    NOT NULL DEFAULT 0.00,
    tax_amount      DECIMAL(12,2)    NOT NULL DEFAULT 0.00,
    total_amount    DECIMAL(12,2)    NOT NULL CHECK (total_amount >= 0),
    notes           TEXT             DEFAULT NULL,
    version         INT UNSIGNED     NOT NULL DEFAULT 1,       -- optimistic lock
    created_at      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                     ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_order_number   (order_number),
    INDEX       idx_order_user    (user_id, created_at DESC),
    INDEX       idx_order_status  (status, created_at DESC),   -- admin "all pending" dashboard
    INDEX       idx_order_created (created_at DESC),           -- date-range sales reports
    CONSTRAINT fk_order_user
        FOREIGN KEY (user_id)    REFERENCES users     (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_order_address
        FOREIGN KEY (address_id) REFERENCES addresses (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_order_coupon
        FOREIGN KEY (coupon_id)  REFERENCES coupons   (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Immutable copy of delivery address at order placement time.
-- addresses.line1 can be edited or soft-deleted — this row never changes.
CREATE TABLE order_address_snapshots (
    id           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    order_id     BIGINT UNSIGNED  NOT NULL,
    full_name    VARCHAR(200)     NOT NULL,
    phone        VARCHAR(20)      DEFAULT NULL,
    line1        VARCHAR(255)     NOT NULL,
    line2        VARCHAR(255)     DEFAULT NULL,
    city         VARCHAR(100)     NOT NULL,
    state        VARCHAR(100)     NOT NULL,
    postal_code  VARCHAR(20)      NOT NULL,
    country_code CHAR(2)          NOT NULL,
    created_at   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uq_order_addr_snapshot (order_id),
    CONSTRAINT fk_addr_snapshot_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- product_name, sku, variant_label: frozen at purchase time.
-- Never re-read from products / variants — they may be renamed or deleted later.
CREATE TABLE order_items (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_id      BIGINT UNSIGNED   NOT NULL,
    variant_id    BIGINT UNSIGNED   NOT NULL,
    -- ── frozen product snapshot ──────────────────────────────
    product_name  VARCHAR(255)      NOT NULL,                 -- e.g. "Acid-Wash Oversized Tee"
    sku           VARCHAR(100)      NOT NULL,                 -- e.g. "AWT-RED-M"
    variant_label VARCHAR(255)      DEFAULT NULL,             -- e.g. "Red / M"
    -- ── pricing ──────────────────────────────────────────────
    unit_price    DECIMAL(12,2)     NOT NULL CHECK (unit_price >= 0),
    quantity      SMALLINT UNSIGNED NOT NULL CHECK (quantity > 0),
    subtotal      DECIMAL(12,2)     NOT NULL CHECK (subtotal >= 0),   -- unit_price * quantity

    PRIMARY KEY (id),
    INDEX idx_oi_order   (order_id),
    INDEX idx_oi_variant (variant_id),                        -- sales analytics per SKU
    CONSTRAINT fk_oi_order
        FOREIGN KEY (order_id)   REFERENCES orders           (id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_oi_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Append-only status transition log. One INSERT per transition, never UPDATE.
-- from_status NULL = initial order creation entry.
CREATE TABLE order_status_history (
    id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    order_id        BIGINT UNSIGNED  NOT NULL,
    from_status     ENUM(
                        'pending_payment','payment_failed','confirmed','processing',
                        'shipped','out_for_delivery','delivered','cancelled',
                        'return_requested','return_approved','return_rejected',
                        'returned','refund_initiated','refund_completed'
                    )                DEFAULT NULL,
    to_status       ENUM(
                        'pending_payment','payment_failed','confirmed','processing',
                        'shipped','out_for_delivery','delivered','cancelled',
                        'return_requested','return_approved','return_rejected',
                        'returned','refund_initiated','refund_completed'
                    )                NOT NULL,
    note            VARCHAR(500)     DEFAULT NULL,
    tracking_number VARCHAR(100)     DEFAULT NULL,
    carrier         VARCHAR(100)     DEFAULT NULL,
    changed_by      BIGINT UNSIGNED  DEFAULT NULL,             -- NULL = system/webhook
    changed_at      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_osh_order (order_id, changed_at DESC),
    CONSTRAINT fk_osh_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only. No UPDATE or DELETE.';


-- =============================================================================
--  8. PAYMENTS  (Razorpay)
-- =============================================================================

CREATE TABLE payment_transactions (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id            BIGINT UNSIGNED NOT NULL,
    razorpay_order_id   VARCHAR(100)    NOT NULL,
    razorpay_payment_id VARCHAR(100)    DEFAULT NULL,
    razorpay_signature  VARCHAR(255)    DEFAULT NULL,           -- HMAC stored for audit
    idempotency_key     VARCHAR(100)    NOT NULL,               -- client UUID per attempt
    status              ENUM(
                            'created',
                            'attempted',
                            'captured',
                            'failed',
                            'refund_initiated',
                            'partially_refunded',
                            'fully_refunded'
                        )               NOT NULL DEFAULT 'created',
    amount              DECIMAL(12,2)   NOT NULL CHECK (amount > 0),
    currency            CHAR(3)         NOT NULL DEFAULT 'INR',
    failure_reason      VARCHAR(255)    DEFAULT NULL,
    gateway_response    JSON            DEFAULT NULL,           -- full raw webhook payload
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_pt_razorpay_order  (razorpay_order_id),
    UNIQUE KEY  uq_pt_idempotency_key (idempotency_key),        -- duplicate-charge guard
    INDEX       idx_pt_order          (order_id),
    INDEX       idx_pt_status         (status),
    CONSTRAINT fk_pt_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- One row per Razorpay refund call. Partial refunds = multiple rows per transaction.
CREATE TABLE refunds (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_transaction_id BIGINT UNSIGNED NOT NULL,
    return_request_id      BIGINT UNSIGNED DEFAULT NULL,
    razorpay_refund_id     VARCHAR(100)    NOT NULL,
    amount                 DECIMAL(12,2)   NOT NULL CHECK (amount > 0),
    status                 ENUM('initiated','processed','failed')
                                           NOT NULL DEFAULT 'initiated',
    reason                 VARCHAR(255)    DEFAULT NULL,
    gateway_response       JSON            DEFAULT NULL,
    created_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                           ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_razorpay_refund_id (razorpay_refund_id),
    INDEX       idx_refund_payment    (payment_transaction_id),
    CONSTRAINT fk_refund_payment
        FOREIGN KEY (payment_transaction_id) REFERENCES payment_transactions (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  9. RETURNS
-- =============================================================================

CREATE TABLE return_requests (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id      BIGINT UNSIGNED NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    reason        ENUM('defective','wrong_item','not_as_described','changed_mind','other')
                                  NOT NULL,
    reason_detail TEXT            DEFAULT NULL,
    status        ENUM(
                      'pending',
                      'approved',
                      'rejected',
                      'pickup_scheduled',
                      'item_received',
                      'refund_initiated',
                      'refund_completed'
                  )               NOT NULL DEFAULT 'pending',
    refund_amount DECIMAL(12,2)   DEFAULT NULL,
    refund_method ENUM('original_payment','store_credit','bank_transfer')
                                  DEFAULT NULL,
    admin_notes   TEXT            DEFAULT NULL,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                  ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_rr_order  (order_id),
    INDEX idx_rr_user   (user_id),
    INDEX idx_rr_status (status),
    CONSTRAINT fk_rr_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_rr_user
        FOREIGN KEY (user_id)  REFERENCES users  (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE return_items (
    id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    return_request_id BIGINT UNSIGNED   NOT NULL,
    order_item_id     BIGINT UNSIGNED   NOT NULL,
    quantity          SMALLINT UNSIGNED NOT NULL CHECK (quantity > 0),
    condition_note    VARCHAR(500)      DEFAULT NULL,

    PRIMARY KEY (id),
    INDEX idx_ri_return     (return_request_id),
    INDEX idx_ri_order_item (order_item_id),
    CONSTRAINT fk_ri_return
        FOREIGN KEY (return_request_id) REFERENCES return_requests (id)
        ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_ri_order_item
        FOREIGN KEY (order_item_id)     REFERENCES order_items      (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  10. REVIEWS
-- =============================================================================
-- order_id FK enforces verified-purchase gate at DB level.
-- Unique on (user_id, order_id, product_id): one review per purchase event —
-- not one-per-product lifetime. A repeat buyer can review each purchase separately.

CREATE TABLE reviews (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id    BIGINT UNSIGNED NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    order_id      BIGINT UNSIGNED NOT NULL,                    -- verified purchase gate
    rating        TINYINT UNSIGNED NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title         VARCHAR(255)    DEFAULT NULL,
    body          TEXT            DEFAULT NULL,
    is_verified   TINYINT(1)      NOT NULL DEFAULT 1,
    is_approved   TINYINT(1)      NOT NULL DEFAULT 0,          -- admin moderation gate
    is_deleted    TINYINT(1)      NOT NULL DEFAULT 0,
    helpful_count INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                   ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY  uq_review_user_order_product (user_id, order_id, product_id),
    INDEX       idx_review_product           (product_id, is_approved, is_deleted, created_at DESC),
    INDEX       idx_review_rating            (product_id, rating),
    CONSTRAINT fk_rev_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_rev_user
        FOREIGN KEY (user_id)    REFERENCES users    (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_rev_order
        FOREIGN KEY (order_id)   REFERENCES orders   (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE review_media (
    id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    review_id     BIGINT UNSIGNED   NOT NULL,
    cloudinary_id VARCHAR(500)      NOT NULL,
    media_type    ENUM('image','video') NOT NULL DEFAULT 'image',
    display_order SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_review_media (review_id, display_order),
    CONSTRAINT fk_review_media
        FOREIGN KEY (review_id) REFERENCES reviews (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  11. NOTIFICATIONS
-- =============================================================================

CREATE TABLE notification_log (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    recipient_email  VARCHAR(254)    NOT NULL,
    template_name    VARCHAR(100)    NOT NULL,                 -- 'order_confirmation'
    channel          ENUM('email','sms','push') NOT NULL DEFAULT 'email',
    reference_type   VARCHAR(50)     NOT NULL,                 -- 'ORDER', 'RETURN', 'PAYMENT'
    reference_id     BIGINT UNSIGNED NOT NULL,
    status           ENUM('queued','sent','delivered','failed','bounced')
                                     NOT NULL DEFAULT 'queued',
    sendgrid_msg_id  VARCHAR(255)    DEFAULT NULL,
    payload          JSON            DEFAULT NULL,             -- template variable snapshot
    error_message    VARCHAR(500)    DEFAULT NULL,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_notif_user      (user_id, created_at DESC),
    INDEX idx_notif_reference (reference_type, reference_id),
    INDEX idx_notif_status    (status, created_at DESC),       -- retry failed notifications
    CONSTRAINT fk_notif_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


SET foreign_key_checks = 1;


-- =============================================================================
--  IMPLEMENTATION NOTES
-- =============================================================================
--
--  OPTIMISTIC LOCKING  (version column)
--    Applies to: users, products, orders, inventory_records
--    JPA: @Version annotation — throws OptimisticLockException on conflict.
--
--    Reserve stock at checkout:
--      UPDATE inventory_records
--         SET qty_reserved = qty_reserved + ?,
--             version      = version + 1
--       WHERE variant_id = ?
--         AND version    = ?
--         AND (qty_available - qty_reserved) >= ?;
--    0 rows affected = conflict → return 409, ask user to retry.
--
--    Commit stock on payment captured:
--      UPDATE inventory_records
--         SET qty_available = qty_available - ?,
--             qty_reserved  = qty_reserved  - ?
--       WHERE variant_id = ?;
--
--  SOFT DELETES
--    Tables with is_deleted: users, products, categories, coupons,
--                            addresses, reviews, wishlist_items.
--    All queries must append: WHERE is_deleted = 0
--    For high-traffic tables consider a generated column for a unique partial index:
--      active_slug VARCHAR(200) AS (IF(is_deleted=0, slug, NULL)) STORED,
--      UNIQUE KEY uq_active_slug (active_slug)
--
--  REDIS CACHING
--    product:{slug}      → product detail,   TTL 10 min
--    categories:tree     → full tree,         TTL 30 min
--    cart:{userId}       → cart (primary),    TTL 30 days
--    inv:{variantId}     → sellable qty,      TTL 60 s
--    coupon:{code}       → validity check,    TTL 5 min
--    Invalidation: on write event, delete the specific key (not namespace flush).
--
--  ELASTICSEARCH SYNC
--    Denormalized product document: availableSizes[], availableColors[],
--    avgRating, totalStock, primaryImageUrl, categoryName.
--    Write path: catalog/inventory save → Spring ApplicationEvent
--                → @Async listener → ES upsert.
--    Nightly full-reindex job (cron 0 0 3 * * *) as drift safety net.
--
--  PARTITIONING  (once tables exceed ~50 M rows)
--    orders, audit_logs, order_status_history, notification_log
--      → PARTITION BY RANGE (YEAR(created_at))
--    Partition pruning makes date-range admin queries skip irrelevant years.
--
--  READ REPLICA ROUTING
--    @Transactional(readOnly = true) → replica data source.
--    Spring AbstractRoutingDataSource switches on the flag.
--    INSERTs / UPDATEs / DELETEs always go to primary.
--
--  MONETARY PRECISION
--    DECIMAL(12,2): up to ₹9,999,999,999.99. Never FLOAT or DOUBLE.
--
-- =============================================================================
