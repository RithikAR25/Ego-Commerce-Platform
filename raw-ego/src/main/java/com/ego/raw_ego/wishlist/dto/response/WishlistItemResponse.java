package com.ego.raw_ego.wishlist.dto.response;

import com.ego.raw_ego.catalog.entity.InventoryRecord;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single wishlist item with live variant data from the catalog.
 *
 * <p>Unlike order items, wishlist items reflect the current live catalog state:
 * current price, current stock status. Prices shown here are subject to change
 * (they are NOT snapshots).
 */
@Getter
@Builder
public class WishlistItemResponse {

    private Long id;

    /** The variant saved to the wishlist. */
    private Long variantId;

    /** Current SKU of the variant. */
    private String sku;

    /** Current product name. */
    private String productName;

    /** Human-readable variant label, e.g. "Black / M". */
    private String variantLabel;

    /** Current live selling price. */
    private BigDecimal price;

    /** Current compare-at (crossed-out) price. Null if no discount. */
    private BigDecimal compareAtPrice;

    /** Calculated discount percentage. Null if no compareAtPrice. */
    private Integer discountPercent;

    /** Primary variant image URL. Null if no image uploaded. */
    private String primaryImageUrl;

    /** Current stock status. */
    private InventoryRecord.StockStatus stockStatus;

    /** When this item was added to the wishlist. */
    private Instant addedAt;
}
