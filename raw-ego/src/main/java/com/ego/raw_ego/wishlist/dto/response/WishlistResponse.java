package com.ego.raw_ego.wishlist.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The user's full wishlist — a list of items with live variant data.
 */
@Getter
@Builder
public class WishlistResponse {

    /** All wishlisted items, oldest-added first. */
    private List<WishlistItemResponse> items;

    /** Total number of items. */
    private int itemCount;
}
