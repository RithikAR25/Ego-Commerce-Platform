/**
 * useOrderPolling.ts
 *
 * Polls GET /orders/{id} until the order reaches a terminal state (CONFIRMED)
 * or until the maximum attempts are exhausted.
 *
 * Architecture:
 *  - Razorpay webhook is source-of-truth. We cannot trust the Checkout.js
 *    success callback alone — the order stays PENDING_PAYMENT until the
 *    webhook fires and our backend processes it.
 *  - We poll every POLL_INTERVAL_MS for up to MAX_ATTEMPTS × POLL_INTERVAL_MS
 *    total (default: 2s × 15 = 30 seconds).
 *  - If already CONFIRMED on first fetch → resolves instantly.
 *  - Cleans up on unmount to prevent state updates on dead components.
 *
 * Usage:
 *   const { status, order } = useOrderPolling(orderId);
 *   // status: 'polling' | 'confirmed' | 'timeout' | 'already_confirmed' | 'error' | 'idle'
 */

import { useState, useEffect, useRef, useCallback } from 'react';
import { getOrder } from '@/api/order.api';
import type { OrderDetail } from '@/types/order.types';

// ── Constants ─────────────────────────────────────────────────────────────────

const POLL_INTERVAL_MS = 2_000;
const MAX_ATTEMPTS     = 15; // 30 seconds total

// ── Types ─────────────────────────────────────────────────────────────────────

export type PollingStatus =
  | 'idle'              // orderId not yet provided
  | 'polling'           // actively polling
  | 'confirmed'         // order reached CONFIRMED within polling window
  | 'already_confirmed' // order was already CONFIRMED on first fetch
  | 'timeout'           // max attempts exhausted; webhook may still arrive late
  | 'error';            // unexpected fetch failure

export interface UseOrderPollingResult {
  status:  PollingStatus;
  order:   OrderDetail | null;
  attempts: number;
}

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Poll a specific order until it reaches CONFIRMED status.
 *
 * @param orderId - the EGO order ID to poll. Pass 0 / null to keep idle.
 */
export const useOrderPolling = (orderId: number | null): UseOrderPollingResult => {
  const [status,   setStatus]   = useState<PollingStatus>('idle');
  const [order,    setOrder]    = useState<OrderDetail | null>(null);
  const [attempts, setAttempts] = useState(0);

  // Refs for cleanup safety — avoid state updates after unmount
  const isMounted     = useRef(true);
  const intervalRef   = useRef<ReturnType<typeof setInterval> | null>(null);
  const attemptCount  = useRef(0);

  const stopPolling = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  }, []);

  useEffect(() => {
    isMounted.current = true;
    return () => {
      isMounted.current = false;
      stopPolling();
    };
  }, [stopPolling]);

  useEffect(() => {
    if (!orderId || orderId <= 0) {
      setStatus('idle');
      return;
    }

    // Reset state when a new orderId is provided
    setStatus('polling');
    setOrder(null);
    setAttempts(0);
    attemptCount.current = 0;
    stopPolling();

    const poll = async () => {
      attemptCount.current += 1;
      if (isMounted.current) setAttempts(attemptCount.current);

      try {
        const fetchedOrder = await getOrder(orderId);

        if (!isMounted.current) return;

        setOrder(fetchedOrder);

        if (fetchedOrder.status === 'CONFIRMED') {
          stopPolling();
          // Distinguish "already confirmed" (attempt 1) from "became confirmed" during polling
          setStatus(attemptCount.current === 1 ? 'already_confirmed' : 'confirmed');
          return;
        }

        // Terminal non-CONFIRMED states (CANCELLED, REFUNDED) — stop polling
        if (fetchedOrder.status === 'CANCELLED' || fetchedOrder.status === 'REFUNDED') {
          stopPolling();
          setStatus('error');
          return;
        }

        // Still PENDING_PAYMENT — check if we've hit the limit
        if (attemptCount.current >= MAX_ATTEMPTS) {
          stopPolling();
          setStatus('timeout');
        }
        // else: let the interval fire the next attempt
      } catch (err) {
        console.error('[useOrderPolling] Fetch error:', err);
        if (!isMounted.current) return;
        stopPolling();
        setStatus('error');
      }
    };

    // Fire immediately, then on each interval
    poll();
    intervalRef.current = setInterval(poll, POLL_INTERVAL_MS);

    return () => stopPolling();
  }, [orderId, stopPolling]);

  return { status, order, attempts };
};
