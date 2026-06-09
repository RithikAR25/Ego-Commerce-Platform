/**
 * useReturns.ts
 *
 * TanStack Query hooks for all Return & Refund operations (Phase 10).
 *
 * Architecture:
 * - useInitiateReturn()       → useMutation — POST /orders/{orderId}/returns
 *                               On success: invalidates order detail + return query
 * - useOrderReturn(orderId)   → useQuery    — GET /orders/{orderId}/returns
 * - useAdminReturns()         → useQuery    — GET /admin/returns (paginated, filterable)
 * - useAdminReturn(returnId)  → useQuery    — GET /admin/returns/{returnId}
 * - useAdminReviewReturn()    → useMutation — PUT /admin/returns/{returnId}/review
 *                               On success: invalidates return + order detail queries
 *
 * Rule: never call return API functions directly in useEffect or components.
 * Always use these hooks.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  initiateReturn,
  getOrderReturn,
  adminGetReturns,
  adminGetReturn,
  adminReviewReturn,
} from '@/api/return.api';
import type { InitiateReturnPayload, AdminReviewReturnPayload, ReturnStatus } from '@/types/return.types';
import { orderKeys } from '@/features/orders/hooks/useOrders';
import { toast } from '@/store/uiStore';

// ── Query key factory ─────────────────────────────────────────────────────────

export const returnKeys = {
  all:          ['returns'] as const,
  lists:        () => [...returnKeys.all, 'list'] as const,
  listFiltered: (status: ReturnStatus | undefined, page: number) =>
    [...returnKeys.lists(), status, page] as const,
  byOrder:      (orderId: number) => [...returnKeys.all, 'order', orderId] as const,
  detail:       (returnId: number) => [...returnKeys.all, 'detail', returnId] as const,
};

// ── useInitiateReturn ─────────────────────────────────────────────────────────

/**
 * Customer submits a return request for a delivered order.
 *
 * On success:
 * - Invalidates the order return query (return status now visible)
 * - Invalidates the order detail query (order context may display return badge)
 * - Shows a success toast
 *
 * On error:
 * - Shows the backend error message (e.g. "Only DELIVERED orders are eligible",
 *   "Return window has expired", "Return request already exists")
 */
export const useInitiateReturn = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ orderId, payload }: { orderId: number; payload: InitiateReturnPayload }) =>
      initiateReturn(orderId, payload),
    onSuccess: (returnRequest) => {
      // Cache the new return directly — no need to refetch
      queryClient.setQueryData(returnKeys.byOrder(returnRequest.orderId), returnRequest);
      // Invalidate order detail so it reflects the return context
      queryClient.invalidateQueries({ queryKey: orderKeys.detail(returnRequest.orderId) });
      toast.success('Return request submitted. Our team will review it shortly.');
    },
    onError: (error: { response?: { data?: { message?: string } } }) => {
      const message = error.response?.data?.message ?? 'Failed to submit return request.';
      toast.error(message);
    },
  });
};

// ── useOrderReturn ────────────────────────────────────────────────────────────

/**
 * Customer retrieves the return request status for a specific order.
 * Returns undefined if no return request exists (404 from backend).
 * Only enabled when orderId is a valid positive number.
 *
 * Stale after 30 seconds — return status may be updated by admin.
 *
 * @param orderId the EGO order ID to check return status for
 */
export const useOrderReturn = (orderId: number) =>
  useQuery({
    queryKey: returnKeys.byOrder(orderId),
    queryFn:  () => getOrderReturn(orderId),
    enabled:  orderId > 0,
    staleTime: 30_000,
    retry: (failureCount, error: { response?: { status?: number } }) => {
      // Don't retry on 404 — no return request exists for this order
      if (error?.response?.status === 404) return false;
      return failureCount < 3;
    },
  });

// ── useAdminReturns ───────────────────────────────────────────────────────────

/**
 * Admin retrieves all return requests, optionally filtered by status.
 * Paginated — newest first.
 * Stale after 30 seconds — return status may change frequently during review.
 *
 * @param status optional filter (REQUESTED, APPROVED, REJECTED, REFUND_INITIATED, REFUND_COMPLETED)
 * @param page   0-indexed page number (default 0)
 * @param size   items per page (default 20)
 */
export const useAdminReturns = (status?: ReturnStatus, page = 0, size = 20) =>
  useQuery({
    queryKey: returnKeys.listFiltered(status, page),
    queryFn:  () => adminGetReturns(status, page, size),
    staleTime: 30_000,
  });

// ── useAdminReturn ────────────────────────────────────────────────────────────

/**
 * Admin retrieves a specific return request by ID.
 * Only enabled when returnId is a valid positive number.
 * Stale after 30 seconds.
 *
 * @param returnId the return request ID
 */
export const useAdminReturn = (returnId: number) =>
  useQuery({
    queryKey: returnKeys.detail(returnId),
    queryFn:  () => adminGetReturn(returnId),
    enabled:  returnId > 0,
    staleTime: 30_000,
  });

// ── useAdminReviewReturn ──────────────────────────────────────────────────────

/**
 * Admin approves or rejects a return request.
 *
 * On approval success:
 * - Razorpay refund is initiated server-side
 * - Updates the return detail cache with the REFUND_COMPLETED response
 * - Invalidates the admin returns list (status has changed)
 * - Invalidates the order detail (order is now REFUNDED)
 * - Shows success toast with refund confirmation
 *
 * On rejection success:
 * - Updates the return detail cache with the REJECTED response
 * - Invalidates the admin returns list
 * - Shows rejection confirmation toast
 *
 * On error:
 * - Shows the backend error message (e.g. "Refund amount exceeds grand total",
 *   "Cannot process Razorpay refund: no payment ID", "Return already reviewed")
 */
export const useAdminReviewReturn = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      returnId,
      payload,
    }: {
      returnId: number;
      payload: AdminReviewReturnPayload;
    }) => adminReviewReturn(returnId, payload),
    onSuccess: (returnRequest, variables) => {
      // Update the return detail cache immediately
      queryClient.setQueryData(returnKeys.detail(variables.returnId), returnRequest);
      // Invalidate the returns list (status changed — needs to be re-sorted/filtered)
      queryClient.invalidateQueries({ queryKey: returnKeys.lists() });
      // Invalidate the order detail (status may have changed to REFUNDED)
      queryClient.invalidateQueries({ queryKey: orderKeys.detail(returnRequest.orderId) });

      if (variables.payload.approve) {
        toast.success(
          'Return approved. Razorpay refund initiated — ₹' +
          (returnRequest.refundAmount ?? 0) + ' will be refunded within 5–7 business days.',
        );
      } else {
        toast.success('Return request rejected.');
      }
    },
    onError: (error: { response?: { data?: { message?: string } } }) => {
      const message = error.response?.data?.message ?? 'Failed to review return request.';
      toast.error(message);
    },
  });
};
