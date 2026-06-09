package com.ego.raw_ego.catalog.event;

import com.ego.raw_ego.catalog.enums.ProductStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published when a product's stock status changes
 * automatically (ACTIVE ↔ OUT_OF_STOCK transitions driven by inventory updates).
 *
 * <p>Published by {@link com.ego.raw_ego.catalog.service.ProductService#syncProductStatusFromInventory}
 * and consumed by {@link com.ego.raw_ego.wishlist.service.WishlistStockNotificationListener}
 * to notify users who have wishlisted a variant of this product.
 *
 * <p>Using Spring events keeps the catalog module decoupled from the wishlist and
 * notification modules — ProductService does not import either.
 */
@Getter
public class ProductStockStatusChangedEvent extends ApplicationEvent {

    /** The product whose stock status changed. */
    private final Long productId;

    /** The product's display name (for the email subject). */
    private final String productName;

    /** The new stock status after the transition. */
    private final ProductStatus newStatus;

    public ProductStockStatusChangedEvent(Object source,
                                          Long productId,
                                          String productName,
                                          ProductStatus newStatus) {
        super(source);
        this.productId   = productId;
        this.productName = productName;
        this.newStatus   = newStatus;
    }
}
