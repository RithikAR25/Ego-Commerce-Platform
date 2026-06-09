package com.ego.raw_ego.order.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/orders/checkout}.
 *
 * <p><b>Address resolution (new flow):</b> Provide {@code addressId} to use a saved address
 * from the user's address book. The address is snapshotted to JSON at checkout time so
 * future edits never retroactively change order history.
 *
 * <p><b>Legacy fallback:</b> If {@code addressId} is null, {@code shippingAddress} is used
 * as a plain-text fallback for backwards compatibility with the old checkout flow.
 *
 * <p><b>Coupon (Phase 9):</b> {@code couponCode} is optional. If provided,
 * the coupon is validated and its discount applied to the subtotal inside the
 * checkout transaction. An invalid code returns {@code 400 Bad Request}.
 */
@Getter
@NoArgsConstructor
public class CheckoutRequest {

    /**
     * ID of a saved address from the user's address book.
     * If provided, takes precedence over {@code shippingAddress}.
     * The address is loaded and snapshotted to JSON inside the checkout transaction.
     */
    private Long addressId;

    /**
     * Legacy free-text shipping address (used only if {@code addressId} is null).
     * Example: "123 MG Road, Bengaluru, Karnataka 560001"
     */
    @Size(max = 1000, message = "Shipping address must not exceed 1000 characters.")
    private String shippingAddress;

    /**
     * Optional coupon code to apply a discount to the order subtotal.
     * Case-insensitive. Validated and applied atomically inside the checkout transaction.
     * Null or blank = no coupon applied.
     */
    @Size(max = 50, message = "Coupon code must not exceed 50 characters.")
    private String couponCode;
}
