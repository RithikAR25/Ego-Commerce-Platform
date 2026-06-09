package com.ego.raw_ego.order.service;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.address.entity.UserAddress;
import com.ego.raw_ego.address.service.AddressService;
import com.ego.raw_ego.cart.dto.response.CartItemResponse;
import com.ego.raw_ego.cart.dto.response.CartResponse;
import com.ego.raw_ego.cart.service.CartService;
import com.ego.raw_ego.cart.service.InventoryReservationService;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.util.HtmlSanitizer;
import com.ego.raw_ego.coupon.entity.Coupon;
import com.ego.raw_ego.coupon.service.CouponService;
import com.ego.raw_ego.notification.event.OrderDeliveredEvent;
import com.ego.raw_ego.notification.event.OrderPlacedEvent;
import com.ego.raw_ego.notification.event.OrderShippedEvent;
import com.ego.raw_ego.order.dto.request.CheckoutRequest;
import com.ego.raw_ego.order.dto.request.UpdateOrderStatusRequest;
import com.ego.raw_ego.order.dto.response.OrderDetailResponse;
import com.ego.raw_ego.order.dto.response.OrderItemResponse;
import com.ego.raw_ego.order.dto.response.OrderStatusHistoryResponse;
import com.ego.raw_ego.order.dto.response.OrderSummaryResponse;
import com.ego.raw_ego.order.entity.Order;
import com.ego.raw_ego.order.entity.OrderItem;
import com.ego.raw_ego.order.entity.OrderStatusHistory;
import com.ego.raw_ego.order.enums.OrderStatus;
import com.ego.raw_ego.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Core order business logic.
 *
 * <h3>Checkout pipeline (CRITICAL — read before modifying)</h3>
 * <ol>
 *   <li>Load cart from Redis (via CartService)</li>
 *   <li>Validate cart is not empty and all items are purchasable</li>
 *   <li>For each item: commit inventory (quantity_available -= qty, quantity_reserved -= qty)</li>
 *   <li>Persist Order + OrderItems + initial OrderStatusHistory</li>
 *   <li>Clear Redis cart</li>
 * </ol>
 *
 * <p>Steps 3–5 are inside a single {@code @Transactional} boundary.
 * If {@code clearCart()} throws (Redis error), the transaction rolls back:
 * inventory is un-committed and the order row is not persisted.
 *
 * <h3>Cancellation pipeline</h3>
 * Only {@code PENDING_PAYMENT} orders can be cancelled.
 * Cancellation restores {@code quantity_available} by calling
 * {@link InventoryReservationService#restore(Long, int)} for each line item.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository            orderRepository;
    private final CartService                cartService;
    private final InventoryReservationService reservationService;
    private final ApplicationEventPublisher   eventPublisher;
    private final CouponService              couponService;
    private final AddressService             addressService;
    private final RedisTemplate<String, String> redisTemplate;

    /** Redis lock TTL — matches ego.cart.checkout-lock-ttl-minutes in application.properties. */
    @Value("${ego.cart.checkout-lock-ttl-minutes:10}")
    private int checkoutLockTtlMinutes;

    private static final String CHECKOUT_LOCK_PREFIX = "checkout:lock:";

    // ── Checkout lock helpers ─────────────────────────────────────────────────

    /**
     * Acquires a per-user Redis lock using SET NX (set if not exists).
     * Returns {@code true} if the lock was acquired (safe to proceed),
     * {@code false} if the lock is already held (duplicate in-flight request).
     *
     * <p>The lock expires automatically after {@code checkoutLockTtlMinutes} minutes,
     * so a crash or unhandled exception never leaves the user locked out permanently.
     */
    private boolean acquireCheckoutLock(Long userId) {
        String lockKey = CHECKOUT_LOCK_PREFIX + userId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofMinutes(checkoutLockTtlMinutes));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Releases the per-user checkout lock immediately after checkout completes
     * (success or failure), so the user can retry if payment was not accepted.
     */
    private void releaseCheckoutLock(Long userId) {
        redisTemplate.delete(CHECKOUT_LOCK_PREFIX + userId);
    }

    // ── Checkout ──────────────────────────────────────────────────────────────

    /**
     * Converts the user's Redis cart into a persisted Order.
     *
     * <p><b>Transactional boundary:</b> inventory commit, order persist, and cart
     * clear all occur in one unit. Any failure rolls back everything.
     *
     * @param user    the authenticated customer
     * @param request checkout payload (shipping address)
     * @return full order detail response with items and initial status history
     * @throws ConflictException         if cart is empty, any item is out of stock,
     *                                   or a race condition depletes stock at commit time
     */
    @Transactional
    public OrderDetailResponse checkout(User user, CheckoutRequest request) {
        // ─1. Checkout double-submit guard ────────────────────────────────────
        // Acquires a per-user Redis lock (SET NX, TTL = ego.cart.checkout-lock-ttl-minutes).
        // Prevents a second in-flight checkout from the same user (e.g. double-tap on "Pay Now")
        // from reaching the inventory commit pipeline before the first response arrives.
        if (!acquireCheckoutLock(user.getId())) {
            throw new ConflictException(
                    "A checkout is already in progress for your account. " +
                    "Please wait a moment and try again.");
        }

        try {
            return doCheckout(user, request);
        } finally {
            // Always release the lock so retry is possible after a failure.
            releaseCheckoutLock(user.getId());
        }
    }

    /**
     * Internal checkout implementation — called within the Redis lock guard.
     * All inventory commits, order persist, and cart clear happen in this
     * single {@code @Transactional} boundary.
     */
    @Transactional
    public OrderDetailResponse doCheckout(User user, CheckoutRequest request) {
        // 0. Email verification guard
        if (!user.isEmailVerified()) {
            throw new ConflictException(
                    "Please verify your email address before placing an order. " +
                    "Check your inbox or request a new verification email from your account settings.");
        }

        // 1. Load cart
        CartResponse cart = cartService.getCart(user.getId());

        if (cart.getItems().isEmpty()) {
            throw new ConflictException("Cannot checkout: your cart is empty.");
        }

        // 2. Validate all items are purchasable
        for (CartItemResponse item : cart.getItems()) {
            if (item.getStockStatus() == com.ego.raw_ego.catalog.entity.InventoryRecord.StockStatus.OUT_OF_STOCK) {
                throw new ConflictException(
                        "Item \"" + item.getProductName() + " – " + item.getVariantLabel() +
                        "\" is out of stock. Please remove it from your cart and try again.");
            }
        }

        // 3. Commit inventory for each item (atomic decrement of quantity_available + quantity_reserved)
        //    ConflictException propagates out here if stock runs out in a race
        for (CartItemResponse item : cart.getItems()) {
            reservationService.commit(item.getVariantId(), item.getQuantity());
        }

        // 4. Validate and apply coupon (if provided) — inside this transaction so any
        //    ConflictException (bad code, expired, limit reached) rolls back inventory commits.
        BigDecimal discountAmount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            CouponService.CouponApplicationResult couponResult =
                    couponService.validateAndComputeDiscount(request.getCouponCode(), cart.getSubtotal());
            appliedCoupon = couponResult.coupon();
            discountAmount = couponResult.discountAmount();
        }

        // 5. Build and persist Order + OrderItems + initial history.
        //    saveAndFlush() is used (not save()) to force an immediate DB roundtrip
        //    so all generated values (@Id, @CreationTimestamp, @UpdateTimestamp,
        //    and cascade-child IDs) are populated in the returned entity before
        //    we map it to the DTO. Using save() alone can leave them null within
        //    the same transaction.
        Order order = buildOrder(user, request, cart, discountAmount, appliedCoupon);
        OrderStatusHistory initialHistory = OrderStatusHistory.builder()
                .status(OrderStatus.PENDING_PAYMENT)
                .note("Order placed by customer.")
                .build();
        order.addStatusHistory(initialHistory);
        Order savedOrder = orderRepository.saveAndFlush(order);

        // 6. Persist OrderCoupon audit record and increment currentUses — same transaction.
        if (appliedCoupon != null) {
            couponService.recordUsage(appliedCoupon, savedOrder.getId(), discountAmount);
        }

        // 7. Clear cart (inside same transaction — Redis failure = full rollback)
        cartService.clearCart(user.getId());

        log.info("Order placed: orderId={} userId={} grandTotal={} discount={}",
                savedOrder.getId(), user.getId(), savedOrder.getGrandTotal(), discountAmount);

        // 8. Publish event AFTER saveAndFlush — order is committed before async listener reads it.
        //    The async listener runs on ego-async-* threads (Phase 8 — SendGrid notification).
        //    It never blocks this transaction and cannot roll it back.
        eventPublisher.publishEvent(new OrderPlacedEvent(this, savedOrder.getId(), user.getId()));

        return toDetailResponse(savedOrder);
    }

    // ── Customer reads ────────────────────────────────────────────────────────

    /**
     * Returns paginated order history for the authenticated user.
     *
     * @param userId   the authenticated user's ID
     * @param pageable Spring pagination parameters
     */
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toSummaryResponse);
    }

    /**
     * Returns full order detail for a specific order.
     *
     * <p>Ownership check: if the order does not belong to {@code userId}
     * and the caller is not an admin, a {@link ResourceNotFoundException}
     * is thrown — not a 403 — to avoid leaking order existence.
     *
     * @param orderId the order to fetch
     * @param userId  the authenticated user's ID
     * @param isAdmin true if the caller has ROLE_ADMIN (bypasses ownership check)
     */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId, Long userId, boolean isAdmin) {
        Order order = isAdmin
                ? orderRepository.findById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found: id=" + orderId))
                : orderRepository.findByIdAndUserId(orderId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found: id=" + orderId));
        return toDetailResponse(order);
    }

    // ── Customer cancel ───────────────────────────────────────────────────────

    /**
     * Cancels a customer order.
     *
     * <p>Only orders in {@code PENDING_PAYMENT} status may be cancelled by the customer.
     * Cancellation restores {@code quantity_available} for each line item.
     *
     * @param orderId the order to cancel
     * @param userId  the authenticated customer's ID (ownership enforced)
     * @return updated order detail with CANCELLED status
     * @throws ResourceNotFoundException if the order doesn't exist or doesn't belong to the user
     * @throws ConflictException         if the order is not in PENDING_PAYMENT status
     */
    @Transactional
    public OrderDetailResponse cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: id=" + orderId));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(
                    "Only PENDING_PAYMENT orders can be cancelled. Current status: " + order.getStatus());
        }

        // ── Snapshot items into a plain List BEFORE any restore() call ──────────
        // InventoryReservationService.restore() uses @Modifying(clearAutomatically=true),
        // which calls em.clear() after the bulk UPDATE. This evicts ALL entities from
        // Hibernate's 1st-level cache — including `order` — making it detached.
        // Any lazy collection access on a detached entity throws LazyInitializationException.
        // Solution: force-initialize the items collection into a regular Java List now
        // (while the entity is still managed), then iterate that plain list for restore calls.
        List<OrderItem> lineItems = new java.util.ArrayList<>(order.getItems());

        // Restore quantity_available for each line item.
        // Each call internally triggers em.clear(), so `order` becomes detached after this loop.
        for (OrderItem item : lineItems) {
            reservationService.restore(item.getVariantId(), item.getQuantity());
        }

        // ── Re-fetch a fresh managed Order after em.clear() ──────────────────────
        // The original `order` reference is detached. We need a managed entity
        // so that addStatusHistory() can lazy-load statusHistory and toDetailResponse()
        // can access items + statusHistory within the still-open session.
        Order freshOrder = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: id=" + orderId));

        freshOrder.setStatus(OrderStatus.CANCELLED);
        OrderStatusHistory cancelHistory = OrderStatusHistory.builder()
                .status(OrderStatus.CANCELLED)
                .note("Cancelled by customer.")
                .build();
        freshOrder.addStatusHistory(cancelHistory);

        log.info("Order cancelled: orderId={} userId={}", orderId, userId);
        return toDetailResponse(freshOrder);
    }

    // ── Admin operations ──────────────────────────────────────────────────────

    /**
     * Admin: returns all orders, optionally filtered by status (newest first).
     *
     * @param status   optional status filter — null returns all orders
     * @param pageable pagination parameters
     */
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> adminGetOrders(OrderStatus status, Pageable pageable) {
        return orderRepository.findAllByStatusFilter(status, pageable)
                .map(this::toSummaryResponse);
    }

    /**
     * Admin: advances an order's status to the next stage.
     *
     * <p>The state machine is enforced by
     * {@link OrderStatus#assertValidAdminTransition(OrderStatus, OrderStatus)}.
     *
     * @param orderId the order to update
     * @param request target status + optional note
     * @return updated order detail
     * @throws ResourceNotFoundException if the order doesn't exist
     * @throws IllegalArgumentException  if the transition is invalid (mapped to 400)
     */
    @Transactional
    public OrderDetailResponse adminUpdateStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: id=" + orderId));

        OrderStatus.assertValidAdminTransition(order.getStatus(), request.getStatus());

        order.setStatus(request.getStatus());

        // ── Persist shipment tracking info when courier has received the order ────
        // Shipment fields are set (and potentially updated) when advancing to SHIPPED
        // or OUT_FOR_DELIVERY. We only overwrite existing values if the admin provides
        // new ones — a null request field leaves the existing DB value untouched.
        if (request.getStatus() == OrderStatus.SHIPPED ||
                request.getStatus() == OrderStatus.OUT_FOR_DELIVERY) {
            if (request.getTrackingNumber() != null) {
                order.setTrackingNumber(request.getTrackingNumber());
            }
            if (request.getCourierName() != null) {
                order.setCourierName(request.getCourierName());
            }
            if (request.getTrackingUrl() != null) {
                order.setTrackingUrl(request.getTrackingUrl());
            }
            if (request.getEstimatedDeliveryAt() != null) {
                order.setEstimatedDeliveryAt(request.getEstimatedDeliveryAt());
            }
        }

        OrderStatusHistory historyEntry = OrderStatusHistory.builder()
                .status(request.getStatus())
                .note(request.getNote())
                .build();
        order.addStatusHistory(historyEntry);

        // saveAndFlush() forces immediate DB roundtrip so the new OrderStatusHistory entry's
        // @CreationTimestamp is populated in the returned entity before DTO mapping.
        Order savedOrder = orderRepository.saveAndFlush(order);

        // ── Publish status-change notification events ──────────────────────────
        // Events are published AFTER saveAndFlush() — the DB record is committed
        // before any async listener opens its own Hibernate session to read the order.
        Long customerId = savedOrder.getUser().getId();
        if (request.getStatus() == OrderStatus.SHIPPED) {
            eventPublisher.publishEvent(new OrderShippedEvent(this, savedOrder.getId(), customerId));
        } else if (request.getStatus() == OrderStatus.DELIVERED) {
            eventPublisher.publishEvent(new OrderDeliveredEvent(this, savedOrder.getId(), customerId));
        }

        log.info("Order status advanced: orderId={} → {} trackingNumber={} courier={}",
                orderId, request.getStatus(), order.getTrackingNumber(), order.getCourierName());
        return toDetailResponse(savedOrder);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private Order buildOrder(User user, CheckoutRequest request, CartResponse cart,
                              BigDecimal discountAmount, Coupon appliedCoupon) {
        // grandTotal = subtotal - discount + shippingTotal
        BigDecimal grandTotal = cart.getSubtotal()
                .subtract(discountAmount)
                .add(BigDecimal.ZERO) // shippingTotal — always ZERO for now
                .max(BigDecimal.ZERO); // cannot go negative

        // ── Resolve shipping address ──────────────────────────────────────────
        // Priority: addressId (saved address) > shippingAddress (legacy free-text)
        String resolvedShippingAddress = null;
        String resolvedAddressSnapshot = null;

        if (request.getAddressId() != null) {
            // Load saved address and snapshot it to JSON — changes to the address book
            // after this point will NOT affect this order's shipping details.
            UserAddress addr = addressService.getForCheckout(user.getId(), request.getAddressId());
            resolvedShippingAddress = addr.toSnapshotLine(); // human-readable fallback
            resolvedAddressSnapshot = buildAddressJson(addr);
        } else if (request.getShippingAddress() != null && !request.getShippingAddress().isBlank()) {
            resolvedShippingAddress = HtmlSanitizer.sanitize(request.getShippingAddress(), 500);
        } else {
            throw new ConflictException("Shipping address is required. Please select a saved address or enter one manually.");
        }

        Order order = Order.builder()
                .user(user)
                .shippingAddress(resolvedShippingAddress)
                .addressSnapshot(resolvedAddressSnapshot)
                .subtotal(cart.getSubtotal())
                .shippingTotal(BigDecimal.ZERO)
                .discountAmount(discountAmount)
                .couponCodeSnapshot(appliedCoupon != null ? appliedCoupon.getCode() : null)
                .grandTotal(grandTotal)
                .build();

        for (CartItemResponse cartItem : cart.getItems()) {
            BigDecimal lineTotal = cartItem.getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .variantId(cartItem.getVariantId())
                    .skuSnapshot(cartItem.getSku())
                    .productNameSnapshot(cartItem.getProductName())
                    .variantLabelSnapshot(cartItem.getVariantLabel())
                    .primaryImageUrlSnapshot(cartItem.getPrimaryImageUrl())
                    .unitPriceSnapshot(cartItem.getPrice())
                    .quantity(cartItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build();

            order.addItem(item);
        }

        return order;
    }

    /**
     * Serialises a {@link UserAddress} into a compact JSON string for order snapshot storage.
     * Uses string concatenation to avoid pulling the full Jackson ObjectMapper into the service.
     * The format is intentionally simple — all values are escaped for basic injection safety.
     */
    private String buildAddressJson(UserAddress a) {
        return "{"
            + jsonField("fullName",    a.getFullName())    + ","
            + jsonField("phone",       a.getPhone())       + ","
            + jsonField("addressLine1", a.getAddressLine1()) + ","
            + jsonField("addressLine2", a.getAddressLine2() != null ? a.getAddressLine2() : "") + ","
            + jsonField("landmark",    a.getLandmark() != null ? a.getLandmark() : "") + ","
            + jsonField("city",        a.getCity())        + ","
            + jsonField("state",       a.getState())       + ","
            + jsonField("pinCode",     a.getPinCode())     + ","
            + jsonField("country",     a.getCountry())     + ","
            + jsonField("addressType", a.getAddressType().name())
            + "}";
    }

    private String jsonField(String key, String value) {
        String safe = (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "\"" + key + "\":\"" + safe + "\"";
    }

    private OrderDetailResponse toDetailResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .variantId(item.getVariantId())
                        .skuSnapshot(item.getSkuSnapshot())
                        .productNameSnapshot(item.getProductNameSnapshot())
                        .variantLabelSnapshot(item.getVariantLabelSnapshot())
                        .primaryImageUrlSnapshot(item.getPrimaryImageUrlSnapshot())
                        .unitPrice(item.getUnitPriceSnapshot())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        List<OrderStatusHistoryResponse> history = order.getStatusHistory().stream()
                .map(h -> OrderStatusHistoryResponse.builder()
                        .status(h.getStatus())
                        .note(h.getNote())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();

        return OrderDetailResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .shippingTotal(order.getShippingTotal())
                .discountAmount(order.getDiscountAmount())
                .couponCode(order.getCouponCodeSnapshot())
                .grandTotal(order.getGrandTotal())
                .shippingAddress(order.getShippingAddress())
                .razorpayOrderId(order.getRazorpayOrderId())
                .trackingNumber(order.getTrackingNumber())
                .courierName(order.getCourierName())
                .trackingUrl(order.getTrackingUrl())
                .estimatedDeliveryAt(order.getEstimatedDeliveryAt())
                .items(items)
                .statusHistory(history)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderSummaryResponse toSummaryResponse(Order order) {
        int itemCount = order.getItems().stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();

        return OrderSummaryResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .grandTotal(order.getGrandTotal())
                .itemCount(itemCount)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
