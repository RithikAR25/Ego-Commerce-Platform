package com.ego.raw_ego.catalog.enums;

/**
 * Lifecycle status of a product in the EGO catalog.
 *
 * <p>Transition rules (enforced by {@link com.ego.raw_ego.catalog.service.ProductService}):
 * <pre>
 *   DRAFT        → ACTIVE         (admin publishes)
 *   ACTIVE       → OUT_OF_STOCK   (auto: triggered when last variant hits 0 stock)
 *   OUT_OF_STOCK → ACTIVE         (auto: triggered when any variant is restocked)
 *   ACTIVE       → ARCHIVED       (admin archives)
 *   DRAFT        → ARCHIVED       (admin discards)
 *   ARCHIVED     → DRAFT          (admin restores for editing)
 * </pre>
 *
 * <p>Public API visibility:
 * <ul>
 *   <li>DRAFT        — hidden from storefront, visible in admin</li>
 *   <li>ACTIVE        — visible and purchasable in storefront</li>
 *   <li>OUT_OF_STOCK  — visible in storefront (with badge), not purchasable</li>
 *   <li>ARCHIVED      — hidden from storefront and public APIs</li>
 * </ul>
 */
public enum ProductStatus {

    /**
     * Product is being created/edited. Not visible to customers.
     * Default state when a product is first created.
     */
    DRAFT,

    /**
     * Product is live. Visible and purchasable in the storefront.
     * At least one variant must have quantity_available > 0.
     */
    ACTIVE,

    /**
     * All variants have quantity_available = 0.
     * Visible in storefront with "Out of Stock" badge — customers cannot add to cart.
     * Automatically transitions back to ACTIVE when stock is replenished.
     */
    OUT_OF_STOCK,

    /**
     * Product has been permanently retired. Hidden from storefront and public APIs.
     * Admin can restore to DRAFT if needed.
     */
    ARCHIVED
}
