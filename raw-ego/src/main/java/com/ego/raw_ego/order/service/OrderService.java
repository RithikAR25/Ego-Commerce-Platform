package com.ego.raw_ego.order.service;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.order.dto.request.CheckoutRequest;
import com.ego.raw_ego.order.dto.request.UpdateOrderStatusRequest;
import com.ego.raw_ego.order.dto.response.OrderDetailResponse;
import com.ego.raw_ego.order.dto.response.OrderSummaryResponse;
import com.ego.raw_ego.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for core order business logic.
 *
 * <h3>Checkout pipeline (CRITICAL — read before modifying)</h3>
 * <ol>
 *   <li>Load cart from Redis (via CartService)</li>
 *   <li>Validate cart is not empty and all items are purchasable</li>
 *   <li>For each item: commit inventory (quantity_available -= qty, quantity_reserved -= qty)</li>
 *   <li>Persist Order + OrderItems + initial OrderStatusHistory</li>
 *   <li>Clear Redis cart</li>
 * </ol>
 * Steps 3–5 are inside a single {@code @Transactional} boundary.
 *
 * <h3>Cancellation pipeline</h3>
 * Only {@code PENDING_PAYMENT} orders can be cancelled.
 */
public interface OrderService {

    /**
     * Converts the user's Redis cart into a persisted Order.
     * Includes a per-user Redis checkout lock to prevent double-submit.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if cart is empty,
     *         any item is out of stock, or a checkout is already in progress
     */
    OrderDetailResponse checkout(User user, CheckoutRequest request);

    /**
     * Internal checkout implementation (called within the Redis lock guard).
     * Should not be called directly by controllers.
     */
    OrderDetailResponse doCheckout(User user, CheckoutRequest request);

    /** Paginated order history for a customer. */
    Page<OrderSummaryResponse> getOrders(Long userId, Pageable pageable);

    /**
     * Returns full order detail. If {@code isAdmin=true}, ownership check is bypassed.
     *
     * @throws com.ego.raw_ego.common.exception.ResourceNotFoundException if order not found
     */
    OrderDetailResponse getOrderDetail(Long orderId, Long userId, boolean isAdmin);

    /**
     * Cancels a {@code PENDING_PAYMENT} order and restores inventory.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if order is not cancellable
     */
    OrderDetailResponse cancelOrder(Long orderId, Long userId);

    /** Admin: paginated order listing with optional status filter. */
    Page<OrderSummaryResponse> adminGetOrders(OrderStatus status, Pageable pageable);

    /**
     * Admin: manual status transition for an order (e.g., CONFIRMED → SHIPPED → DELIVERED).
     */
    OrderDetailResponse adminUpdateStatus(Long orderId, UpdateOrderStatusRequest request);
}
