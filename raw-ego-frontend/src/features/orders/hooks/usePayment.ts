/**
 * usePayment.ts
 *
 * Single orchestration hook for the complete checkout → payment flow.
 *
 * Encapsulates the 3-step payment pipeline so CheckoutPage stays clean:
 *   Step 1 — POST /orders/checkout            → create EGO order
 *   Step 2 — POST /payments/razorpay/create   → create Razorpay order
 *   Step 3 — window.Razorpay(options).open()  → launch payment modal
 *
 * On Razorpay success callback:
 *   → navigate to /checkout/verify/:orderId   (webhook polling screen)
 *
 * On Razorpay dismiss/failure:
 *   → show toast, reset state to 'idle' so customer can retry
 *
 * Security guarantee:
 *   The EGO order advances to CONFIRMED ONLY via the backend webhook.
 *   The Razorpay success callback NEVER marks the order as paid directly.
 *   We navigate to the verification screen and poll until the webhook fires.
 *
 * Usage:
 *   const { placeOrderAndPay, paymentState, error, isPaying } = usePayment();
 *   <button onClick={() => placeOrderAndPay({ shippingAddress, couponCode })} />
 */

import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { checkout } from '@/api/order.api';
import { createPaymentOrder } from '@/api/payment.api';
import { useRazorpay } from '@/hooks/useRazorpay';
import { useQueryClient } from '@tanstack/react-query';
import { orderKeys } from '@/features/orders/hooks/useOrders';
import { cartKeys } from '@/features/cart/hooks/useCart';
import { useCartStore } from '@/store/cartStore';
import { toast } from '@/store/uiStore';
import type { CheckoutPayload } from '@/types/order.types';

// ── Payment flow states ───────────────────────────────────────────────────────

export type PaymentFlowState =
  | 'idle'             // ready to accept a new payment attempt
  | 'placing_order'    // Step 1: POST /orders/checkout in flight
  | 'creating_payment' // Step 2: POST /payments/razorpay/create in flight
  | 'modal_open'       // Step 3: Razorpay modal is open
  | 'verifying'        // Success callback fired; navigating to polling screen
  | 'failed';          // An unrecoverable error occurred

// ── Return type ───────────────────────────────────────────────────────────────

export interface UsePaymentReturn {
  /**
   * Initiates the full checkout → payment pipeline.
   *
   * @param payload - { shippingAddress, couponCode? }
   */
  placeOrderAndPay: (payload: CheckoutPayload) => Promise<void>;
  /** Current state of the payment flow. */
  paymentState: PaymentFlowState;
  /** True when any async operation is in progress. */
  isPaying: boolean;
  /** Whether the Razorpay script is ready (use to disable Pay button). */
  isGatewayReady: boolean;
  /** Whether the script failed to load (show error message). */
  isGatewayFailed: boolean;
  /** User-facing error message, if any. */
  error: string | null;
  /** Reset to idle — allows the customer to retry after a failure. */
  reset: () => void;
}

// ── Hook ──────────────────────────────────────────────────────────────────────

export const usePayment = (): UsePaymentReturn => {
  const navigate     = useNavigate();
  const queryClient  = useQueryClient();
  const resetCart    = useCartStore((s) => s.resetCart);

  const { openPaymentModal, isScriptLoaded, isScriptFailed } = useRazorpay();

  const [paymentState, setPaymentState] = useState<PaymentFlowState>('idle');
  const [error,        setError]        = useState<string | null>(null);

  const reset = useCallback(() => {
    setPaymentState('idle');
    setError(null);
  }, []);

  const placeOrderAndPay = useCallback(
    async (payload: CheckoutPayload) => {
      // Guard: prevent duplicate clicks
      if (paymentState !== 'idle') return;

      setError(null);

      // ── Step 1: Create EGO order ──────────────────────────────────────────
      setPaymentState('placing_order');
      let egoOrder;
      try {
        egoOrder = await checkout(payload);
      } catch (err: unknown) {
        const msg =
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message
          ?? 'Could not place order. Please check your cart and try again.';
        setError(msg);
        setPaymentState('failed');
        toast.error(msg);
        return;
      }

      // ── Step 2: Create Razorpay payment order ─────────────────────────────
      setPaymentState('creating_payment');
      let paymentOrder;
      try {
        paymentOrder = await createPaymentOrder({ orderId: egoOrder.id });
      } catch (err: unknown) {
        const msg =
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message
          ?? 'Could not initialise payment gateway. Please try paying from your orders page.';
        setError(msg);
        setPaymentState('failed');
        toast.error(msg);
        // Order exists but payment not started — the customer can retry payment
        // from the Orders page. Invalidate orders list so it appears.
        queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
        return;
      }

      // Invalidate order detail cache now that razorpayOrderId is populated
      queryClient.invalidateQueries({ queryKey: orderKeys.detail(egoOrder.id) });

      // ── Step 3: Open Razorpay modal ───────────────────────────────────────
      setPaymentState('modal_open');

      openPaymentModal(paymentOrder, {
        onSuccess: (_response) => {
          // Razorpay success callback fired.
          // IMPORTANT: Order is still PENDING_PAYMENT until the backend
          // webhook processes the payment.captured event. We navigate to
          // the verification page which polls until CONFIRMED.
          setPaymentState('verifying');

          // Clear cart from cache — it was consumed by checkout()
          queryClient.setQueryData(cartKeys.cart(), {
            items: [], itemCount: 0, subtotal: 0,
          });
          resetCart();

          // Navigate to webhook polling screen
          navigate(`/checkout/verify/${egoOrder.id}`);
        },

        onDismiss: () => {
          // User closed the modal without paying.
          // Order exists as PENDING_PAYMENT — they can retry from Orders page.
          toast.info(
            'Payment cancelled. You can complete payment from My Orders.',
          );
          // Invalidate orders list so the new order appears
          queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
          // Reset to idle so the Pay button re-enables on the checkout page
          setPaymentState('idle');
        },

        onError: (message) => {
          // Razorpay reported a payment failure (card declined, bank error, etc.)
          setError(message);
          setPaymentState('failed');
          toast.error(message);
          // Invalidate orders list — order exists as PENDING_PAYMENT
          queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
        },
      });
    },
    [paymentState, openPaymentModal, navigate, queryClient, resetCart],
  );

  return {
    placeOrderAndPay,
    paymentState,
    isPaying: paymentState !== 'idle' && paymentState !== 'failed',
    isGatewayReady:  isScriptLoaded,
    isGatewayFailed: isScriptFailed,
    error,
    reset,
  };
};
