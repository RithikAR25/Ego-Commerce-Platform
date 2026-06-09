/**
 * order.types.ts
 *
 * TypeScript interfaces for the order domain.
 * These MUST mirror the exact backend DTO field names — never invent field names.
 *
 * Key backend DTOs this maps to:
 *   - OrderDetailResponse.java
 *   - OrderSummaryResponse.java
 *   - OrderItemResponse.java
 *   - OrderStatusHistoryResponse.java
 *   - CheckoutRequest.java
 *   - UpdateOrderStatusRequest.java
 *   - PaymentOrderResponse.java        (Phase 7)
 *   - CreatePaymentOrderRequest.java   (Phase 7)
 */

// ── Enums ──────────────────────────────────────────────────────────────────────

/**
 * Mirrors OrderStatus.java enum.
 * Terminal states: DELIVERED, CANCELLED, REFUNDED.
 */
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

// ── Response types ─────────────────────────────────────────────────────────────

/**
 * Matches OrderItemResponse.java.
 * All product/price fields are immutable snapshots from checkout time.
 */
export interface OrderItem {
  id:                     number;
  variantId:              number;
  /** SKU at checkout time — e.g. "EGO-MEN-0001-BLK-M" */
  skuSnapshot:            string;
  /** Product name at checkout time */
  productNameSnapshot:    string;
  /** Variant label at checkout time — e.g. "Black / M". Null if no attributes. */
  variantLabelSnapshot:   string | null;
  /** CDN URL of the primary image at checkout time. Null if no image existed. */
  primaryImageUrlSnapshot: string | null;
  /** Price per unit at checkout time (immutable) */
  unitPrice:              number;
  quantity:               number;
  /** Pre-computed: unitPrice × quantity */
  lineTotal:              number;
}

/**
 * Matches OrderStatusHistoryResponse.java.
 * One entry per status transition — append-only audit trail.
 */
export interface OrderStatusHistory {
  status:    OrderStatus;
  /** Optional admin or system note. Null if not provided. */
  note:      string | null;
  createdAt: string;
}

/**
 * Matches OrderSummaryResponse.java — lightweight view for order list rows.
 */
export interface OrderSummary {
  id:         number;
  status:     OrderStatus;
  grandTotal: number;
  /** Total units across all line items */
  itemCount:  number;
  createdAt:  string;
}

/**
 * Matches OrderDetailResponse.java — full order detail including items and audit trail.
 */
export interface OrderDetail {
  id:              number;
  status:          OrderStatus;
  subtotal:        number;
  shippingTotal:   number;
  grandTotal:      number;
  shippingAddress: string;
  /**
   * Razorpay order ID — populated after POST /api/v1/payments/razorpay/create.
   * Null until payment is initiated. Used to open the Checkout.js modal.
   */
  razorpayOrderId: string | null;
  /** Discount applied by coupon. 0 when no coupon used. */
  discountAmount:  number;
  /** Coupon code used at checkout time. Null if no coupon. */
  couponCodeSnapshot: string | null;
  // ── Shipment tracking (populated when order is SHIPPED or OUT_FOR_DELIVERY) ────────────
  /** Courier tracking number (e.g. "DELHIVERY9876543210"). Null until shipment. */
  trackingNumber:      string | null;
  /** Courier name (e.g. "Delhivery", "DTDC", "BlueDart"). Null until shipment. */
  courierName:         string | null;
  /** Deep link to the courier tracking page. Null until shipment. */
  trackingUrl:         string | null;
  /** Estimated delivery date. ISO-8601 UTC string. Null until admin provides it. */
  estimatedDeliveryAt: string | null;
  items:           OrderItem[];
  statusHistory:   OrderStatusHistory[];
  createdAt:       string;
  updatedAt:       string;
}

// ── Request payload types ──────────────────────────────────────────────────────

/** Matches CheckoutRequest.java */
export interface CheckoutPayload {
  /** ID of a saved address. Preferred over shippingAddress in new flow. */
  addressId?: number;
  /** Legacy fallback plain-text address. Used only if addressId is missing. */
  shippingAddress?: string;
  /** Optional coupon code — validated atomically inside the checkout transaction. */
  couponCode?: string;
}

/** Matches UpdateOrderStatusRequest.java — admin only */
export interface UpdateOrderStatusPayload {
  status: OrderStatus;
  note?:  string;
}

// ── Phase 7: Payment types ─────────────────────────────────────────────────────

/**
 * Matches CreatePaymentOrderRequest.java
 * Sent to POST /api/v1/payments/razorpay/create
 */
export interface CreatePaymentOrderPayload {
  orderId: number;
}

/**
 * Matches PaymentOrderResponse.java
 * Contains everything the frontend needs to open the Razorpay Checkout.js modal.
 *
 * Security note: keyId is the PUBLIC Razorpay identifier (rzp_test_...) — safe in browser.
 * The keySecret is NEVER returned to the frontend.
 */
export interface PaymentOrderResponse {
  /** Razorpay order ID — e.g. "order_XXXXXXXXXXXXXXXXXX" */
  razorpayOrderId: string;
  /** Amount in paise (₹1 = 100 paise) */
  amount:          number;
  /** ISO currency code — "INR" */
  currency:        string;
  /** Public Razorpay key ID — safe to use in browser */
  keyId:           string;
  /** EGO order ID — for reference after payment */
  egoOrderId:      number;
}
