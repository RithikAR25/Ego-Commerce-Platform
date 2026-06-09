/**
 * useOrders.ts
 *
 * TanStack Query hooks for all order operations.
 *
 * Architecture:
 * - useCheckout()        → useMutation — POST /orders/checkout
 *                          On success: invalidates cart + orders queries, shows toast
 * - useOrders(page?)     → useQuery    — GET /orders (user history, paginated)
 * - useOrder(orderId)    → useQuery    — GET /orders/{id} (full detail)
 * - useCancelOrder()     → useMutation — POST /orders/{id}/cancel
 *                          On success: invalidates the specific order query
 * - useCreatePayment()   → useMutation — POST /payments/razorpay/create  [Phase 7]
 *                          On success: returns PaymentOrderResponse for Checkout.js modal
 *
 * Rule: never call order API functions directly in useEffect or components.
 * Always use these hooks.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  checkout,
  getOrders,
  getOrder,
  cancelOrder,
} from '@/api/order.api';
import { createPaymentOrder } from '@/api/payment.api';
import type { CheckoutPayload, CreatePaymentOrderPayload } from '@/types/order.types';
import { cartKeys } from '@/features/cart/hooks/useCart';
import { useCartStore } from '@/store/cartStore';
import { toast } from '@/store/uiStore';

// ── Query key factory ─────────────────────────────────────────────────────────

export const orderKeys = {
  all:    ['orders'] as const,
  lists:  () => [...orderKeys.all, 'list'] as const,
  list:   (page: number) => [...orderKeys.lists(), page] as const,
  detail: (orderId: number) => [...orderKeys.all, 'detail', orderId] as const,
};

// ── useCheckout ───────────────────────────────────────────────────────────────

/**
 * Places an order from the current cart.
 *
 * On success:
 * - Clears the cart query cache (cart is now empty server-side)
 * - Resets the Zustand cart badge to 0
 * - Invalidates the order list to include the new order
 * - Shows a success toast
 *
 * On error:
 * - Shows the backend error message (e.g. "Out of stock", "Cart is empty")
 */
export const useCheckout = () => {
  const queryClient = useQueryClient();
  const resetCart   = useCartStore((s) => s.resetCart);

  return useMutation({
    mutationFn: (payload: CheckoutPayload) => checkout(payload),
    onSuccess: () => {
      // Cart is cleared server-side — reflect that in the cache immediately
      queryClient.setQueryData(cartKeys.cart(), { items: [], itemCount: 0, subtotal: 0 });
      resetCart();
      // Invalidate order list so the new order appears
      queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
      toast.success('Order placed! Proceeding to payment...');
    },
    onError: (error: { response?: { data?: { message?: string } } }) => {
      const message = error.response?.data?.message ?? 'Checkout failed. Please try again.';
      toast.error(message);
    },
  });
};

// ── useOrders ─────────────────────────────────────────────────────────────────

/**
 * Returns the authenticated user's paginated order history.
 * Stale after 60 seconds — orders change infrequently from the customer's perspective.
 *
 * @param page 0-indexed page number (default 0)
 * @param size items per page (default 10)
 */
export const useOrders = (page = 0, size = 10) =>
  useQuery({
    queryKey: orderKeys.list(page),
    queryFn:  () => getOrders(page, size),
    staleTime: 60_000,
  });

// ── useOrder ──────────────────────────────────────────────────────────────────

/**
 * Returns the full detail for a specific order.
 * Only enabled when orderId is a valid positive number.
 * Stale after 30 seconds — status history may update when admin advances status.
 *
 * @param orderId the order to fetch
 */
export const useOrder = (orderId: number) =>
  useQuery({
    queryKey: orderKeys.detail(orderId),
    queryFn:  () => getOrder(orderId),
    enabled:  orderId > 0,
    staleTime: 30_000,
  });

// ── useCancelOrder ────────────────────────────────────────────────────────────

/**
 * Cancels a PENDING_PAYMENT order.
 *
 * On success:
 * - Invalidates the specific order detail query (refetches updated status)
 * - Invalidates the order list (reflects CANCELLED status in list view)
 * - Shows a confirmation toast
 *
 * On error:
 * - Shows the backend error message (e.g. "Only PENDING_PAYMENT orders can be cancelled")
 */
export const useCancelOrder = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (orderId: number) => cancelOrder(orderId),
    onSuccess: (updatedOrder) => {
      // Update the detail cache immediately with the CANCELLED response
      queryClient.setQueryData(orderKeys.detail(updatedOrder.id), updatedOrder);
      // Invalidate the list so the status reflects in list view
      queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
      toast.success('Order cancelled successfully.');
    },
    onError: (error: { response?: { data?: { message?: string } } }) => {
      const message = error.response?.data?.message ?? 'Could not cancel the order.';
      toast.error(message);
    },
  });
};

// ── useCreatePayment ──────────────────────────────────────────────────────────

/**
 * Creates a Razorpay payment order for an existing PENDING_PAYMENT EGO order.
 *
 * This is a separate step from useCheckout — the EGO order is created first,
 * then the customer initiates payment. This allows order confirmation to be
 * shown before the payment modal opens.
 *
 * Usage flow:
 *   1. useCheckout() → EGO order created, orderId returned
 *   2. useCreatePayment().mutateAsync({ orderId }) → PaymentOrderResponse
 *   3. useRazorpay().openPaymentModal(paymentOrder, { onSuccess, onError })
 *   4. Razorpay webhook confirms order → status moves to CONFIRMED
 *
 * On success:
 * - Invalidates the order detail cache so razorpayOrderId is reflected
 * - Returns PaymentOrderResponse for the caller to open Checkout.js
 *
 * On error:
 * - Shows toast with backend error message
 */
export const useCreatePayment = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreatePaymentOrderPayload) => createPaymentOrder(payload),
    onSuccess: (paymentOrder) => {
      // Invalidate the order detail so razorpayOrderId is visible in the cache
      queryClient.invalidateQueries({
        queryKey: orderKeys.detail(paymentOrder.egoOrderId),
      });
    },
    onError: (error: { response?: { data?: { message?: string } } }) => {
      const message =
        error.response?.data?.message ?? 'Failed to initialise payment. Please try again.';
      toast.error(message);
    },
  });
};
