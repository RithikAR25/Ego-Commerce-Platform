package com.ego.raw_ego.cart.controller;

import com.ego.raw_ego.cart.dto.request.AddToCartRequest;
import com.ego.raw_ego.cart.dto.request.MergeCartRequest;
import com.ego.raw_ego.cart.dto.request.UpdateCartItemRequest;
import com.ego.raw_ego.cart.dto.response.CartResponse;
import com.ego.raw_ego.cart.service.CartService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;

/**
 * REST controller for the shopping cart.
 *
 * <p>All endpoints require a valid JWT (anyRequest().authenticated() in SecurityConfig).
 * The authenticated user's ID is resolved from the JWT principal via UserDetailsService.
 *
 * <p>API Contract:
 * <pre>
 * GET    /api/v1/cart                       → Get current cart
 * POST   /api/v1/cart/add                   → Add item to cart
 * PUT    /api/v1/cart/items/{variantId}     → Update item quantity
 * DELETE /api/v1/cart/items/{variantId}     → Remove item from cart
 * DELETE /api/v1/cart                       → Clear entire cart
 * POST   /api/v1/cart/merge                 → Merge anonymous cart on login
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Shopping cart management — add, update, remove, and merge")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class CartController {

    private final CartService    cartService;
    private final UserRepository userRepository;

    // ── GET /api/v1/cart ──────────────────────────────────────────────────────

    @GetMapping
    @Operation(
            summary     = "Get the current user's cart",
            description = "Returns all cart items with live prices and stock status from MySQL. " +
                          "Items for deleted variants are silently omitted."
    )
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = resolveUserId(userDetails);
        CartResponse cart = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    // ── POST /api/v1/cart/add ─────────────────────────────────────────────────

    @PostMapping("/add")
    @Operation(
            summary     = "Add a variant to the cart",
            description = "Adds the specified quantity of a variant to the cart. " +
                          "If the variant is already in the cart, quantities are summed (max 10). " +
                          "Returns 409 if stock is insufficient."
    )
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddToCartRequest request
    ) {
        Long userId = resolveUserId(userDetails);
        CartResponse cart = cartService.addToCart(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart.", cart));
    }

    // ── PUT /api/v1/cart/items/{variantId} ────────────────────────────────────

    @PutMapping("/items/{variantId}")
    @Operation(
            summary     = "Update the quantity of a cart item",
            description = "Sets the absolute quantity for the given variant. " +
                          "The difference from the current quantity is reserved or released in inventory. " +
                          "Use DELETE to remove an item entirely."
    )
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        Long userId = resolveUserId(userDetails);
        CartResponse cart = cartService.updateCartItem(userId, variantId, request);
        return ResponseEntity.ok(ApiResponse.success("Cart updated.", cart));
    }

    // ── DELETE /api/v1/cart/items/{variantId} ─────────────────────────────────

    @DeleteMapping("/items/{variantId}")
    @Operation(
            summary     = "Remove a variant from the cart",
            description = "Removes the line item and releases its inventory reservation."
    )
    public ResponseEntity<ApiResponse<CartResponse>> removeCartItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long variantId
    ) {
        Long userId = resolveUserId(userDetails);
        CartResponse cart = cartService.removeCartItem(userId, variantId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart.", cart));
    }

    // ── DELETE /api/v1/cart ───────────────────────────────────────────────────

    @DeleteMapping
    @Operation(
            summary     = "Clear the entire cart",
            description = "Removes all items and releases all inventory reservations. " +
                          "Used for abandoned cart cleanup and post-order confirmation."
    )
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = resolveUserId(userDetails);
        cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared."));
    }

    // ── POST /api/v1/cart/merge ───────────────────────────────────────────────

    @PostMapping("/merge")
    @Operation(
            summary     = "Merge anonymous cart into authenticated cart",
            description = "Called by the frontend immediately after login with the browser's session ID. " +
                          "Items in the anonymous cart are merged into the user cart (max qty wins). " +
                          "The anonymous cart is deleted after merging. No-op if the session has no cart."
    )
    public ResponseEntity<ApiResponse<CartResponse>> mergeCart(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MergeCartRequest request
    ) {
        Long userId = resolveUserId(userDetails);
        CartResponse cart = cartService.mergeAnonymousCart(request.getSessionId(), userId);
        return ResponseEntity.ok(ApiResponse.success("Cart merged successfully.", cart));
    }

    // ── Internal helper ───────────────────────────────────────────────────────

    /**
     * Resolves the authenticated user's database ID from their email (JWT subject).
     * Throws 404 if the user is not found (should not happen in normal flow).
     */
    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmailAndDeletedFalse(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."))
                .getId();
    }
}
