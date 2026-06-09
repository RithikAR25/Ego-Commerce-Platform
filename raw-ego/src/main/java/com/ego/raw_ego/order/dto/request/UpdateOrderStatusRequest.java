package com.ego.raw_ego.order.dto.request;

import com.ego.raw_ego.order.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request body for {@code PUT /api/v1/admin/orders/{orderId}/status}.
 *
 * <p>The {@code note} field is optional — admins may leave a message
 * (e.g. "Payment confirmed manually", "Courier handoff: Delhivery AWB #12345").
 *
 * <p>The shipment tracking fields ({@code trackingNumber}, {@code courierName},
 * {@code trackingUrl}, {@code estimatedDeliveryAt}) are optional but recommended
 * when advancing to {@code SHIPPED} or {@code OUT_FOR_DELIVERY}. The service
 * persists them on the order and returns them in the response so the customer
 * can track their shipment.
 *
 * <p>The transition validity is enforced by
 * {@link OrderStatus#assertValidAdminTransition(OrderStatus, OrderStatus)}.
 */
@Getter
@NoArgsConstructor
public class UpdateOrderStatusRequest {

    @NotNull(message = "Target status is required.")
    private OrderStatus status;

    /** Optional admin note attached to this status transition. */
    private String note;

    // ── Shipment tracking (optional — set when advancing to SHIPPED or OUT_FOR_DELIVERY) ──

    /**
     * Courier tracking number.
     * Example: "DTDC123456789IN", "DELHIVERY9876543210".
     * Persisted on the order when provided.
     */
    @Size(max = 100, message = "Tracking number must not exceed 100 characters.")
    private String trackingNumber;

    /**
     * Courier/logistics partner name.
     * Example: "Delhivery", "DTDC", "BlueDart", "Ekart".
     */
    @Size(max = 100, message = "Courier name must not exceed 100 characters.")
    private String courierName;

    /**
     * Deep link to the courier's customer-facing tracking page.
     * Example: "https://www.delhivery.com/track/package/DELHIVERY9876543210".
     */
    @Size(max = 500, message = "Tracking URL must not exceed 500 characters.")
    private String trackingUrl;

    /**
     * Estimated delivery date to communicate to the customer.
     * ISO-8601 instant string from the client.
     */
    private Instant estimatedDeliveryAt;
}
