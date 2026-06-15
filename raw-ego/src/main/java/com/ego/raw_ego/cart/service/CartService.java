package com.ego.raw_ego.cart.service;

import com.ego.raw_ego.cart.dto.request.AddToCartRequest;
import com.ego.raw_ego.cart.dto.request.UpdateCartItemRequest;
import com.ego.raw_ego.cart.dto.response.CartResponse;

/**
 * Contract for the Redis-backed shopping cart with MySQL inventory reservation.
 *
 * <p><b>Redis data model:</b>
 * <pre>
 *   Key:   "cart:{userId}"  (authenticated) | "cart:anon:{sessionId}" (anonymous)
 *   Type:  Hash
 *   Field: "{variantId}"   (string-coerced Long)
 *   Value: "{quantity}"    (string-coerced Integer)
 *   TTL:   7 days, refreshed on every mutation
 * </pre>
 */
public interface CartService {

    /**
     * Returns the current cart for the authenticated user.
     * Dead variants (deleted from catalog) are silently omitted.
     */
    CartResponse getCart(Long userId);

    /**
     * Adds a variant to the cart (or increments quantity if already present).
     *
     * @throws com.ego.raw_ego.common.exception.ResourceNotFoundException if variant not found
     * @throws com.ego.raw_ego.common.exception.ConflictException if quantity exceeds 10 or stock insufficient
     */
    CartResponse addToCart(Long userId, AddToCartRequest request);

    /**
     * Sets the quantity of a specific cart line item to an absolute value.
     */
    CartResponse updateCartItem(Long userId, Long variantId, UpdateCartItemRequest request);

    /**
     * Removes a specific variant from the cart and releases its inventory reservation.
     */
    CartResponse removeCartItem(Long userId, Long variantId);

    /**
     * Clears all items from the cart and releases all inventory reservations.
     */
    void clearCart(Long userId);

    /**
     * Merges an anonymous session cart into the authenticated user's cart on login.
     * Best-effort: variants that fail stock validation are silently skipped.
     *
     * @param sessionId the anonymous session identifier
     * @param userId    the now-authenticated user's ID
     */
    CartResponse mergeAnonymousCart(String sessionId, Long userId);
}
