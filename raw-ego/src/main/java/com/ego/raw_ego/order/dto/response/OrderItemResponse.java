package com.ego.raw_ego.order.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Response DTO for a single line item within an order.
 *
 * <p>All product/price fields are snapshot values — they reflect
 * the catalog state at the moment the order was placed, not the
 * current live catalog. These values are immutable.
 */
@Getter
@Builder
public class OrderItemResponse {

    private Long id;

    /** Raw FK to product_variants — retained for analytical purposes. */
    private Long variantId;

    /** SKU at checkout time. Example: "EGO-MEN-0001-BLK-M". */
    private String skuSnapshot;

    /** Product name at checkout time. Example: "Oversized Acid-Wash Tee". */
    private String productNameSnapshot;

    /** Variant label at checkout time. Example: "Black / M". Null if no attributes. */
    private String variantLabelSnapshot;

    /**
     * CDN URL of the variant's primary image at checkout time.
     * Preserved because the Cloudinary asset may be deleted post-purchase.
     * Null if no image existed at checkout time.
     */
    private String primaryImageUrlSnapshot;

    /** Price per unit at checkout time. Immutable. */
    private BigDecimal unitPrice;

    /** Quantity purchased. */
    private int quantity;

    /** Pre-computed: unitPrice × quantity. */
    private BigDecimal lineTotal;
}
