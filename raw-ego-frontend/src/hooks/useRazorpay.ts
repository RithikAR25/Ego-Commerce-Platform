/**
 * useRazorpay.ts  (Phase 12 — hardened)
 *
 * Production-grade Razorpay Checkout.js lifecycle manager.
 *
 *  Singleton guard  — script is loaded exactly once per session regardless
 *                     of how many times the hook mounts/unmounts (React
 *                     StrictMode double-invoke safe).
 *  Timeout          — 10-second script load timeout; sets isScriptFailed=true
 *                     so callers can show a meaningful error.
 *  Cleanup          — timeout cleared on unmount; script tag is intentionally
 *                     kept in the DOM (Razorpay expects it for the session).
 *  TypeScript-safe  — full window.Razorpay type declarations; no `any` leakage.
 *  Error recovery   — `window.Razorpay` guard even after script loads (handles
 *                     corporate proxy / content-blocker CDN blocking).
 *
 * Usage:
 *   const { openPaymentModal, isScriptLoaded, isScriptFailed } = useRazorpay();
 *   openPaymentModal(paymentOrder, { onSuccess, onError, onDismiss });
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { useTheme } from '@mui/material';
import type { PaymentOrderResponse } from '@/types/order.types';

// ── Constants ─────────────────────────────────────────────────────────────────

const RAZORPAY_SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';
const RAZORPAY_SCRIPT_ID  = 'razorpay-checkout-js';
const SCRIPT_LOAD_TIMEOUT = 10_000; // 10 seconds

// ── TypeScript declarations ───────────────────────────────────────────────────

/** Razorpay Checkout.js handler — only the methods we use are declared. */
interface RazorpayInstance {
  open(): void;
  on(event: string, handler: (response: unknown) => void): void;
  close(): void;
}

interface RazorpayConstructor {
  new (options: RazorpayOptions): RazorpayInstance;
}

export interface RazorpayOptions {
  key:          string;
  amount:       number;
  currency:     string;
  order_id:     string;
  name:         string;
  description:  string;
  image?:       string;
  prefill?: {
    name?:    string;
    email?:   string;
    contact?: string;
  };
  theme?: {
    color?: string;
    hide_topbar?: boolean;
  };
  handler:       (response: RazorpaySuccessResponse) => void;
  modal?: {
    ondismiss?:    () => void;
    escape?:       boolean;
    backdropclose?: boolean;
  };
  notes?: Record<string, string>;
  retry?: { enabled: boolean };
}

/** Fields Razorpay injects into the success handler response. */
export interface RazorpaySuccessResponse {
  razorpay_order_id:   string;
  razorpay_payment_id: string;
  razorpay_signature:  string;
}

/** Razorpay payment.failed event shape. */
interface RazorpayFailureResponse {
  error?: {
    code?:        string;
    description?: string;
    reason?:      string;
    source?:      string;
    step?:        string;
    field?:       string;
  };
}

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor;
  }
}

// ── Callback types ────────────────────────────────────────────────────────────

export interface PaymentCallbacks {
  /** Called when Razorpay reports payment captured. */
  onSuccess: (response: RazorpaySuccessResponse) => void;
  /** Called when payment fails (network, card decline, etc.) */
  onError?:  (message: string) => void;
  /** Called when the user manually closes the modal without paying. */
  onDismiss?: () => void;
}

// ── Return type ───────────────────────────────────────────────────────────────

interface UseRazorpayReturn {
  /** True once Checkout.js is loaded and window.Razorpay is available. */
  isScriptLoaded:  boolean;
  /** True if the script failed to load (timeout or network error). */
  isScriptFailed:  boolean;
  /** Open the Razorpay payment modal for a payment order. */
  openPaymentModal: (paymentOrder: PaymentOrderResponse, callbacks: PaymentCallbacks) => void;
}

// ── Singleton load state (module-level) ───────────────────────────────────────
// Survives unmount/remount cycles. Prevents double-loading under React StrictMode.

let moduleLoadState: 'idle' | 'loading' | 'loaded' | 'failed' = 'idle';
const moduleLoadListeners: Array<(state: 'loaded' | 'failed') => void> = [];

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Loads Razorpay Checkout.js once per browser session and exposes
 * `openPaymentModal()` to launch the payment sheet.
 *
 * Safe to use in multiple components simultaneously — script is only
 * loaded once regardless of mount count.
 */
export const useRazorpay = (): UseRazorpayReturn => {
  const theme = useTheme();
  const [isScriptLoaded, setIsScriptLoaded] = useState<boolean>(
    () => moduleLoadState === 'loaded' && !!window.Razorpay,
  );
  const [isScriptFailed, setIsScriptFailed] = useState<boolean>(
    () => moduleLoadState === 'failed',
  );

  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    // Already resolved from a previous mount
    if (moduleLoadState === 'loaded') {
      setIsScriptLoaded(true);
      return;
    }
    if (moduleLoadState === 'failed') {
      setIsScriptFailed(true);
      return;
    }

    // Register listener for when the module-level load resolves
    const handleResolution = (state: 'loaded' | 'failed') => {
      if (state === 'loaded') {
        setIsScriptLoaded(true);
        setIsScriptFailed(false);
      } else {
        setIsScriptLoaded(false);
        setIsScriptFailed(true);
      }
    };
    moduleLoadListeners.push(handleResolution);

    // Only the FIRST mount initiates the actual script injection
    if (moduleLoadState === 'idle') {
      moduleLoadState = 'loading';

      // Guard: script element may exist from hot-reload
      if (document.getElementById(RAZORPAY_SCRIPT_ID)) {
        // Script tag exists — Razorpay may already be in window
        if (window.Razorpay) {
          moduleLoadState = 'loaded';
          moduleLoadListeners.forEach((fn) => fn('loaded'));
          moduleLoadListeners.length = 0;
        } else {
          // Tag exists but not yet parsed — wait for existing script to fire
          const existingScript = document.getElementById(RAZORPAY_SCRIPT_ID) as HTMLScriptElement;
          existingScript.addEventListener('load', () => {
            moduleLoadState = 'loaded';
            moduleLoadListeners.forEach((fn) => fn('loaded'));
            moduleLoadListeners.length = 0;
          });
        }
        return;
      }

      const script = document.createElement('script');
      script.id    = RAZORPAY_SCRIPT_ID;
      script.src   = RAZORPAY_SCRIPT_URL;
      script.async = true;
      script.crossOrigin = 'anonymous';

      const fail = () => {
        console.error('[useRazorpay] Failed to load Razorpay Checkout.js');
        moduleLoadState = 'failed';
        moduleLoadListeners.forEach((fn) => fn('failed'));
        moduleLoadListeners.length = 0;
      };

      script.onload = () => {
        if (timeoutRef.current) clearTimeout(timeoutRef.current);
        // Final safety: verify window.Razorpay was actually injected
        if (!window.Razorpay) {
          console.error('[useRazorpay] Script loaded but window.Razorpay not found (blocked?)');
          fail();
          return;
        }
        moduleLoadState = 'loaded';
        moduleLoadListeners.forEach((fn) => fn('loaded'));
        moduleLoadListeners.length = 0;
      };

      script.onerror = () => {
        if (timeoutRef.current) clearTimeout(timeoutRef.current);
        fail();
      };

      document.body.appendChild(script);

      // 10-second timeout guard
      timeoutRef.current = setTimeout(() => {
        if (moduleLoadState === 'loading') {
          console.error('[useRazorpay] Checkout.js load timed out after 10s');
          fail();
        }
      }, SCRIPT_LOAD_TIMEOUT);
    }

    return () => {
      // Remove this instance's listener on unmount
      const idx = moduleLoadListeners.indexOf(handleResolution);
      if (idx !== -1) moduleLoadListeners.splice(idx, 1);
      // Clear timeout only if we own it
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, []);

  /**
   * Opens the Razorpay Checkout.js payment modal.
   *
   * @param paymentOrder - response from POST /api/v1/payments/razorpay/create
   * @param callbacks    - { onSuccess, onError?, onDismiss? }
   */
  const openPaymentModal = useCallback(
    (paymentOrder: PaymentOrderResponse, callbacks: PaymentCallbacks) => {
      if (!isScriptLoaded || !window.Razorpay) {
        const msg = isScriptFailed
          ? 'Payment gateway failed to load. Please refresh the page and try again.'
          : 'Payment gateway is not ready yet. Please wait a moment.';
        callbacks.onError?.(msg);
        return;
      }

      const options: RazorpayOptions = {
        key:         paymentOrder.keyId,      // Public key from backend — never hardcoded
        amount:      paymentOrder.amount,      // Amount in paise — always from server
        currency:    paymentOrder.currency,    // 'INR' — always from server
        order_id:    paymentOrder.razorpayOrderId,
        name:        'EGO',
        description: `Order #${paymentOrder.egoOrderId}`,
        theme:       { color: theme.palette.text.primary, hide_topbar: false },

        notes: {
          ego_order_id: String(paymentOrder.egoOrderId),
        },

        // Disable Razorpay's own retry — we handle retry at the page level
        retry: { enabled: false },

        handler: (response: RazorpaySuccessResponse) => {
          // Razorpay says payment captured.
          // IMPORTANT: This is NOT authoritative — the backend webhook is.
          // We navigate to the verification screen which polls until CONFIRMED.
          callbacks.onSuccess(response);
        },

        modal: {
          escape:        true,
          backdropclose: false, // Prevent accidental close
          ondismiss: () => {
            // User explicitly closed the modal without paying.
            callbacks.onDismiss?.();
          },
        },
      };

      const rzp = new window.Razorpay(options);

      rzp.on('payment.failed', (response: unknown) => {
        const failure = response as RazorpayFailureResponse;
        const msg =
          failure?.error?.description ??
          failure?.error?.reason ??
          'Payment failed. Please try again with a different payment method.';
        callbacks.onError?.(msg);
      });

      rzp.open();
    },
    [isScriptLoaded, isScriptFailed],
  );

  return { isScriptLoaded, isScriptFailed, openPaymentModal };
};
