package com.ego.raw_ego.wishlist.controller;

import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.response.ApiResponse;
import com.ego.raw_ego.wishlist.dto.request.AddToWishlistRequest;
import com.ego.raw_ego.wishlist.dto.response.WishlistResponse;
import com.ego.raw_ego.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the wishlist module.
 *
 * <pre>
 * ── Customer (JWT required) ────────────────────────────────────────────────
 * GET    /api/v1/wishlist                   → get full wishlist (live variant data)
 * POST   /api/v1/wishlist/items             → add variant (idempotent)
 * DELETE /api/v1/wishlist/items/{variantId} → remove variant (idempotent)
 * DELETE /api/v1/wishlist                   → clear entire wishlist
 * </pre>
 *
 * <p>All endpoints require a valid customer JWT. No admin endpoints —
 * wishlists are personal and not admin-managed.
 */
@RestController
@Tag(name = "Wishlist", description = "Customer wishlist management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;
    private final UserRepository  userRepository;

    // ── GET wishlist ──────────────────────────────────────────────────────────

    @GetMapping("/api/v1/wishlist")
    @Operation(
            summary     = "Get wishlist",
            description = "Returns all wishlisted items with live variant data (current price, stock status)."
    )
    public ResponseEntity<ApiResponse<WishlistResponse>> getWishlist(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = resolveUserId(userDetails);
        WishlistResponse response = wishlistService.getWishlist(userId);
        return ResponseEntity.ok(ApiResponse.success("Wishlist retrieved.", response));
    }

    // ── ADD item ──────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/wishlist/items")
    @Operation(
            summary     = "Add item to wishlist",
            description = "Adds a variant to the wishlist. Idempotent — re-adding an existing item returns 200 OK."
    )
    public ResponseEntity<ApiResponse<WishlistResponse>> addItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddToWishlistRequest request
    ) {
        Long userId = resolveUserId(userDetails);
        WishlistResponse response = wishlistService.addItem(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Item added to wishlist.", response));
    }

    // ── REMOVE item ───────────────────────────────────────────────────────────

    @DeleteMapping("/api/v1/wishlist/items/{variantId}")
    @Operation(
            summary     = "Remove item from wishlist",
            description = "Removes a variant from the wishlist. Idempotent — removing a non-existent item returns 200 OK."
    )
    public ResponseEntity<ApiResponse<WishlistResponse>> removeItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long variantId
    ) {
        Long userId = resolveUserId(userDetails);
        WishlistResponse response = wishlistService.removeItem(userId, variantId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from wishlist.", response));
    }

    // ── CLEAR wishlist ────────────────────────────────────────────────────────

    @DeleteMapping("/api/v1/wishlist")
    @Operation(
            summary     = "Clear wishlist",
            description = "Removes all items from the authenticated user's wishlist."
    )
    public ResponseEntity<ApiResponse<Void>> clearWishlist(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = resolveUserId(userDetails);
        wishlistService.clearWishlist(userId);
        return ResponseEntity.ok(ApiResponse.success("Wishlist cleared.", null));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmailAndDeletedFalse(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
