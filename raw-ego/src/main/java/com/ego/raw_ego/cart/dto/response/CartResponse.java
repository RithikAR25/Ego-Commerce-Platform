package com.ego.raw_ego.cart.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full cart response — top-level envelope returned by all cart endpoints.
 *
 * <p>Subtotal is computed server-side from live variant prices to prevent
 * price manipulation via stale client-side cache.
 */
@Getter
@Builder
public class CartResponse {

    /** Ordered list of cart line items. Empty list when cart is empty (never null). */
    private List<CartItemResponse> items;

    /**
     * Total number of units across all line items.
     * Example: 2x T-Shirt + 1x Hoodie = itemCount 3.
     * Displayed as the cart icon badge count.
     */
    private int itemCount;

    /**
     * Sum of (price × quantity) for all items.
     * Computed server-side from live MySQL prices — prevents stale pricing.
     * Does NOT include shipping or discounts (those are Phase 7+ concerns).
     */
    private BigDecimal subtotal;
}
