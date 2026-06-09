package com.ego.raw_ego.cart.dto.response;

import com.ego.raw_ego.catalog.entity.InventoryRecord.StockStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * A single line item in the cart — returned as part of {@link CartResponse}.
 *
 * <p>This DTO intentionally exposes only what the storefront needs:
 * <ul>
 *   <li>Identification: variantId, sku</li>
 *   <li>Display: productName, variantLabel (e.g. "Black / M"), primaryImageUrl</li>
 *   <li>Pricing: price, compareAtPrice, discountPercent</li>
 *   <li>Quantity & availability: quantity, stockStatus</li>
 * </ul>
 *
 * <p><b>NEVER</b> include {@code costPrice} — that is admin-only COGS data.
 */
@Getter
@Builder
public class CartItemResponse {

    /** The variant primary key — used as the cart item identifier in update/remove calls. */
    private Long variantId;

    /** Immutable SKU string (e.g. EGO-TEE-0001-BLK-M). Displayed in cart for reference. */
    private String sku;

    /** The parent product's display name. */
    private String productName;

    /** Human-readable variant label assembled from attribute values (e.g. "Black / M"). */
    private String variantLabel;

    /** Final selling price per unit. */
    private BigDecimal price;

    /** Original (compare-at) price for discount display. Null if no discount. */
    private BigDecimal compareAtPrice;

    /** Pre-computed discount percentage. Null when compareAtPrice is not set. */
    private Integer discountPercent;

    /** CDN thumbnail URL for the primary variant image (or product gallery fallback). */
    private String primaryImageUrl;

    /** Number of units of this variant in the cart. */
    private Integer quantity;

    /**
     * Live stock status fetched from MySQL at cart-read time.
     * If OUT_OF_STOCK, the frontend should show a warning and disable checkout for this item.
     */
    private StockStatus stockStatus;

    /** Units available in inventory (raw number — used for quantity selector max). */
    private Integer quantityAvailable;
}
