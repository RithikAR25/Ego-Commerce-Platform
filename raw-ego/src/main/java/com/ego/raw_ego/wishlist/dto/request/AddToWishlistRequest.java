package com.ego.raw_ego.wishlist.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/wishlist/items}.
 */
@Getter
@NoArgsConstructor
public class AddToWishlistRequest {

    @NotNull(message = "variantId is required.")
    private Long variantId;
}
