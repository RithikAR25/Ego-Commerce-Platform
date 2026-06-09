package com.ego.raw_ego.order.controller;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.response.ApiResponse;
import com.ego.raw_ego.order.dto.request.CheckoutRequest;
import com.ego.raw_ego.order.dto.request.UpdateOrderStatusRequest;
import com.ego.raw_ego.order.dto.response.OrderDetailResponse;
import com.ego.raw_ego.order.dto.response.OrderSummaryResponse;
import com.ego.raw_ego.order.enums.OrderStatus;
import com.ego.raw_ego.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the order domain.
 *
 * <p>API surface:
 * <pre>
 * ── Customer ──────────────────────────────────────────────────────────────
 * POST   /api/v1/orders/checkout           → Place order (authenticated)
 * GET    /api/v1/orders                    → User's paginated order history
 * GET    /api/v1/orders/{orderId}          → Order detail (ownership enforced)
 * POST   /api/v1/orders/{orderId}/cancel   → Cancel PENDING_PAYMENT order
 *
 * ── Admin ──────────────────────────────────────────────────────────────────
 * GET    /api/v1/admin/orders              → All orders (filterable by status)
 * PUT    /api/v1/admin/orders/{orderId}/status → Advance order status
 * </pre>
 *
 * <p>Security:
 * <ul>
 *   <li>Customer endpoints fall under {@code anyRequest().authenticated()} in SecurityConfig.</li>
 *   <li>Admin endpoints are under {@code /api/v1/admin/**} which enforces {@code ROLE_ADMIN}
 *       at the route level. {@code @PreAuthorize} is an additional explicit guard.</li>
 * </ul>
 */
@RestController
@Tag(name = "Orders", description = "Order placement, history, and lifecycle management")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class OrderController {

    private final OrderService   orderService;
    private final UserRepository userRepository;

    // ── POST /api/v1/orders/checkout ─────────────────────────────────────────

    @PostMapping("/api/v1/orders/checkout")
    @Operation(
            summary     = "Place an order",
            description = "Converts the authenticated user's cart into a persisted order. " +
                          "Commits inventory, persists order rows, and clears the Redis cart — " +
                          "all in a single transaction. Returns 409 if cart is empty or stock " +
                          "is exhausted for any item. Returns 400 if shipping address is missing."
    )
    public ResponseEntity<ApiResponse<OrderDetailResponse>> checkout(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CheckoutRequest request
    ) {
        User user = resolveUser(userDetails);
        OrderDetailResponse order = orderService.checkout(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully.", order));
    }

    // ── GET /api/v1/orders ───────────────────────────────────────────────────

    @GetMapping("/api/v1/orders")
    @Operation(
            summary     = "Get order history",
            description = "Returns the authenticated user's orders, newest first. " +
                          "Paginated — use ?page=0&size=10."
    )
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long userId = resolveUserId(userDetails);
        Page<OrderSummaryResponse> orders = orderService.getOrders(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    // ── GET /api/v1/orders/{orderId} ─────────────────────────────────────────

    @GetMapping("/api/v1/orders/{orderId}")
    @Operation(
            summary     = "Get order detail",
            description = "Returns the full order detail including all line items and status history. " +
                          "Returns 404 if the order does not exist or belongs to another user."
    )
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId
    ) {
        Long userId = resolveUserId(userDetails);
        OrderDetailResponse order = orderService.getOrderDetail(orderId, userId, false);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    // ── POST /api/v1/orders/{orderId}/cancel ─────────────────────────────────

    @PostMapping("/api/v1/orders/{orderId}/cancel")
    @Operation(
            summary     = "Cancel an order",
            description = "Cancels the specified order. Only PENDING_PAYMENT orders can be cancelled. " +
                          "Restores quantity_available in inventory for all line items. " +
                          "Returns 409 if the order is already confirmed or beyond."
    )
    public ResponseEntity<ApiResponse<OrderDetailResponse>> cancelOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId
    ) {
        Long userId = resolveUserId(userDetails);
        OrderDetailResponse order = orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully.", order));
    }

    // ── GET /api/v1/admin/orders/{orderId} ───────────────────────────────────

    @GetMapping("/api/v1/admin/orders/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary     = "Admin: get order detail",
            description = "Returns full order detail for any order by ID. Bypasses ownership check."
    )
    public ResponseEntity<ApiResponse<OrderDetailResponse>> adminGetOrderDetail(
            @PathVariable Long orderId
    ) {
        OrderDetailResponse order = orderService.getOrderDetail(orderId, null, true);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    // ── GET /api/v1/admin/orders ─────────────────────────────────────────────

    @GetMapping("/api/v1/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary     = "Admin: list all orders",
            description = "Returns all orders, optionally filtered by status. " +
                          "Omit ?status to return all orders. Newest first. " +
                          "Example: ?status=PENDING_PAYMENT&page=0&size=20"
    )
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> adminGetOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<OrderSummaryResponse> orders = orderService.adminGetOrders(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    // ── PUT /api/v1/admin/orders/{orderId}/status ────────────────────────────

    @PutMapping("/api/v1/admin/orders/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary     = "Admin: advance order status",
            description = "Advances the order to the next status stage. " +
                          "Valid transitions: CONFIRMED→PROCESSING|CANCELLED, PROCESSING→SHIPPED, SHIPPED→DELIVERED. " +
                          "Returns 400 for invalid transitions."
    )
    public ResponseEntity<ApiResponse<OrderDetailResponse>> adminUpdateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        OrderDetailResponse order = orderService.adminUpdateStatus(orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Order status updated.", order));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Resolves the full {@link User} entity from the JWT principal.
     * Used by checkout — the full entity is passed to the service to wire the Order.user FK.
     */
    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmailAndDeletedFalse(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    /**
     * Resolves only the user ID from the JWT principal — cheaper than loading the full entity.
     * Used by read endpoints and cancel where only the ID is needed for ownership checks.
     */
    private Long resolveUserId(UserDetails userDetails) {
        return resolveUser(userDetails).getId();
    }
}
