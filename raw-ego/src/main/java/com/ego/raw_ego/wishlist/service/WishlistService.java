package com.ego.raw_ego.wishlist.service;

import com.ego.raw_ego.wishlist.dto.request.AddToWishlistRequest;
import com.ego.raw_ego.wishlist.dto.response.WishlistResponse;

/**
 * Contract for the wishlist module.
 *
 * <h3>Idempotency</h3>
 * <p>Add: if the variant is already in the wishlist, returns success without a duplicate.
 * Remove: if not in the wishlist, returns success silently.
 *
 * <h3>Live catalog data</h3>
 * <p>{@link #getWishlist} fetches current prices and stock status — not snapshots.
 * Deleted variants are silently dropped from the wishlist on read.
 */
public interface WishlistService {

    /** Returns the full wishlist for a user with live variant data. */
    WishlistResponse getWishlist(Long userId);

    /**
     * Adds a variant to the user's wishlist. Idempotent.
     *
     * @throws com.ego.raw_ego.common.exception.ResourceNotFoundException if the variantId does not exist
     */
    WishlistResponse addItem(Long userId, AddToWishlistRequest request);

    /** Removes a variant from the user's wishlist. Idempotent. */
    WishlistResponse removeItem(Long userId, Long variantId);

    /** Clears the entire wishlist for a user. */
    void clearWishlist(Long userId);
}
