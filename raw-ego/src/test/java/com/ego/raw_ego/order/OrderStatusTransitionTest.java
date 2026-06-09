package com.ego.raw_ego.order;

import com.ego.raw_ego.order.dto.request.UpdateOrderStatusRequest;
import com.ego.raw_ego.order.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for order tracking enhancements:
 * <ul>
 *   <li>OUT_FOR_DELIVERY status transition validation</li>
 *   <li>Updated transition matrix for SHIPPED state</li>
 *   <li>Transition error messages</li>
 * </ul>
 *
 * <p>Pure unit tests — no Spring context, no database.
 */
@DisplayName("Order Tracking — status transitions and validation")
class OrderStatusTransitionTest {

    // ── SHIPPED → OUT_FOR_DELIVERY ────────────────────────────────────────────

    @Nested
    @DisplayName("SHIPPED → OUT_FOR_DELIVERY")
    class ShippedToOutForDelivery {

        @Test
        @DisplayName("valid: SHIPPED → OUT_FOR_DELIVERY does not throw")
        void shipped_to_outForDelivery_isValid() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.assertValidAdminTransition(
                            OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY));
        }
    }

    // ── OUT_FOR_DELIVERY → DELIVERED ──────────────────────────────────────────

    @Nested
    @DisplayName("OUT_FOR_DELIVERY → DELIVERED")
    class OutForDeliveryToDelivered {

        @Test
        @DisplayName("valid: OUT_FOR_DELIVERY → DELIVERED does not throw")
        void outForDelivery_to_delivered_isValid() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.assertValidAdminTransition(
                            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED));
        }
    }

    // ── SHIPPED → DELIVERED (direct) remains valid ────────────────────────────

    @Nested
    @DisplayName("SHIPPED → DELIVERED (direct — still valid)")
    class ShippedToDeliveredDirect {

        @Test
        @DisplayName("valid: SHIPPED → DELIVERED directly still works")
        void shipped_to_delivered_directlyStillValid() {
            assertThatNoException()
                    .isThrownBy(() -> OrderStatus.assertValidAdminTransition(
                            OrderStatus.SHIPPED, OrderStatus.DELIVERED));
        }
    }

    // ── Illegal transitions from OUT_FOR_DELIVERY ─────────────────────────────

    @Nested
    @DisplayName("Illegal transitions from OUT_FOR_DELIVERY")
    class IllegalFromOutForDelivery {

        @Test
        @DisplayName("invalid: OUT_FOR_DELIVERY → CANCELLED throws")
        void outForDelivery_to_cancelled_isInvalid() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> OrderStatus.assertValidAdminTransition(
                            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED))
                    .withMessageContaining("OUT_FOR_DELIVERY");
        }

        @Test
        @DisplayName("invalid: OUT_FOR_DELIVERY → PROCESSING throws")
        void outForDelivery_to_processing_isInvalid() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> OrderStatus.assertValidAdminTransition(
                            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.PROCESSING));
        }

        @Test
        @DisplayName("invalid: OUT_FOR_DELIVERY → SHIPPED (cannot go backwards)")
        void outForDelivery_to_shipped_isInvalid() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> OrderStatus.assertValidAdminTransition(
                            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.SHIPPED));
        }
    }

    // ── DELIVERED is still terminal ───────────────────────────────────────────

    @Nested
    @DisplayName("DELIVERED remains terminal")
    class DeliveredTerminal {

        @Test
        @DisplayName("invalid: DELIVERED → OUT_FOR_DELIVERY is terminal, throws")
        void delivered_to_outForDelivery_isInvalid() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> OrderStatus.assertValidAdminTransition(
                            OrderStatus.DELIVERED, OrderStatus.OUT_FOR_DELIVERY));
        }
    }

    // ── Tracking data fields ──────────────────────────────────────────────────

    @Nested
    @DisplayName("UpdateOrderStatusRequest — tracking fields")
    class TrackingFields {

        /**
         * Verifies that the DTO can hold all 4 shipment tracking fields.
         * Uses a subclass to set private fields for test purposes since @NoArgsConstructor
         * is the only constructor — this mirrors real request deserialization.
         */
        @Test
        @DisplayName("request DTO correctly exposes all 4 tracking fields")
        void trackingFieldsExposed() {
            // Arrange — simulate deserialized JSON via reflection
            UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
            setField(request, "status",              OrderStatus.SHIPPED);
            setField(request, "trackingNumber",      "DTDC123456789IN");
            setField(request, "courierName",         "DTDC");
            setField(request, "trackingUrl",         "https://www.dtdc.in/tracking/DTDC123456789IN");
            setField(request, "estimatedDeliveryAt", Instant.parse("2026-06-10T18:30:00Z"));

            // Assert — all fields are reachable via getters
            assertThat(request.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(request.getTrackingNumber()).isEqualTo("DTDC123456789IN");
            assertThat(request.getCourierName()).isEqualTo("DTDC");
            assertThat(request.getTrackingUrl()).isEqualTo("https://www.dtdc.in/tracking/DTDC123456789IN");
            assertThat(request.getEstimatedDeliveryAt()).isEqualTo(Instant.parse("2026-06-10T18:30:00Z"));
        }

        /** Reflective field setter — mirrors Jackson deserialization of private fields. */
        private void setField(Object target, String name, Object value) {
            try {
                var field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
