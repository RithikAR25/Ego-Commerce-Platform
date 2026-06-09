package com.ego.raw_ego.order.dto.response;

import com.ego.raw_ego.order.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full order detail response — used for the order confirmation screen,
 * order detail page, and admin order view.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code POST /api/v1/orders/checkout} — confirmation response</li>
 *   <li>{@code GET  /api/v1/orders/{orderId}} — customer detail view</li>
 *   <li>{@code POST /api/v1/orders/{orderId}/cancel} — cancellation confirmation</li>
 *   <li>{@code PUT  /api/v1/admin/orders/{orderId}/status} — admin update response</li>
 * </ul>
 */
@Getter
@Builder
public class OrderDetailResponse {

    private Long id;

    /** Current order status. */
    private OrderStatus status;

    /** Sum of line totals (before shipping and discount). */
    private BigDecimal subtotal;

    /** Flat shipping fee. 0.00 until Phase 9. */
    private BigDecimal shippingTotal;

    /**
     * Coupon discount applied to this order (Phase 9).
     * BigDecimal.ZERO if no coupon was applied. Never null.
     */
    private BigDecimal discountAmount;

    /**
     * Coupon code used on this order (Phase 9).
     * Null if no coupon was applied.
     */
    private String couponCode;

    /** Final charged amount: subtotal - discountAmount + shippingTotal. */
    private BigDecimal grandTotal;

    /** Snapshot of the shipping address at checkout time. */
    private String shippingAddress;

    /**
     * Razorpay order ID — set after {@code POST /api/v1/payments/razorpay/create} is called.
     * Null until the customer initiates payment.
     * Used by the frontend to open the Checkout.js modal.
     */
    private String razorpayOrderId;

    // ── Shipment Tracking Fields ──────────────────────────────────────────────

    /**
     * Courier tracking number — set when admin advances order to SHIPPED or OUT_FOR_DELIVERY.
     * Example: "DTDC123456789IN". Null until shipment.
     */
    private String trackingNumber;

    /**
     * Courier/logistics partner name (e.g. "Delhivery", "DTDC", "BlueDart", "Ekart").
     * Null until shipment.
     */
    private String courierName;

    /**
     * Deep link to the courier's tracking page.
     * Example: "https://www.delhivery.com/track/package/DELHIVERY9876543210".
     * Null until shipment.
     */
    private String trackingUrl;

    /**
     * Estimated delivery date set at shipment time.
     * Null until admin provides it when advancing to SHIPPED or OUT_FOR_DELIVERY.
     */
    private Instant estimatedDeliveryAt;

    // ── Collections ──────────────────────────────────────────────────────────

    /** All line items in this order. */
    private List<OrderItemResponse> items;

    /** Chronological audit trail of status changes. */
    private List<OrderStatusHistoryResponse> statusHistory;

    /** Timestamp when the order was placed. */
    private Instant createdAt;

    /** Timestamp of the last status change. */
    private Instant updatedAt;
}
