/**
 * return.api.ts
 *
 * All HTTP calls for the Return & Refund domain (Phase 10).
 *
 * Customer routes: /api/v1/orders/{orderId}/returns  (require authentication)
 * Admin routes:    /api/v1/admin/returns/**           (require ROLE_ADMIN)
 *
 * All requests route through the existing apiClient — JWT interceptor and
 * silent-refresh pipeline apply automatically.
 */

import apiClient from './client';
import type { ApiResponse, PaginatedResponse } from '@/types/api.types';
import type {
  ReturnRequest,
  ReturnStatus,
  InitiateReturnPayload,
  AdminReviewReturnPayload,
} from '@/types/return.types';

// ── POST /api/v1/orders/{orderId}/returns ─────────────────────────────────────

/**
 * Customer submits a return request for a delivered order.
 * Returns 201 Created with the new return request on success.
 * Returns 409 if order is not DELIVERED, window expired, or duplicate request.
 */
export const initiateReturn = async (
  orderId: number,
  payload: InitiateReturnPayload,
): Promise<ReturnRequest> => {
  const { data } = await apiClient.post<ApiResponse<ReturnRequest>>(
    `/orders/${orderId}/returns`,
    payload,
  );
  return data.data;
};

// ── GET /api/v1/orders/{orderId}/returns ──────────────────────────────────────

/**
 * Customer retrieves the status of their return request for a specific order.
 * Returns 404 if no return request exists for this order / user combination.
 */
export const getOrderReturn = async (orderId: number): Promise<ReturnRequest> => {
  const { data } = await apiClient.get<ApiResponse<ReturnRequest>>(
    `/orders/${orderId}/returns`,
  );
  return data.data;
};

// ── GET /api/v1/admin/returns ─────────────────────────────────────────────────

/**
 * Admin: retrieves all return requests, optionally filtered by status.
 * Pass status=undefined to return all requests.
 */
export const adminGetReturns = async (
  status?: ReturnStatus,
  page = 0,
  size = 20,
): Promise<PaginatedResponse<ReturnRequest>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.append('status', status);

  const { data } = await apiClient.get<ApiResponse<PaginatedResponse<ReturnRequest>>>(
    `/admin/returns?${params.toString()}`,
  );
  return data.data;
};

// ── GET /api/v1/admin/returns/{returnId} ──────────────────────────────────────

/**
 * Admin: retrieves a specific return request by ID.
 * Returns 404 if not found.
 */
export const adminGetReturn = async (returnId: number): Promise<ReturnRequest> => {
  const { data } = await apiClient.get<ApiResponse<ReturnRequest>>(
    `/admin/returns/${returnId}`,
  );
  return data.data;
};

// ── PUT /api/v1/admin/returns/{returnId}/review ───────────────────────────────

/**
 * Admin: approves or rejects a return request.
 *
 * On approval (approve=true): Razorpay refund is initiated server-side,
 * order transitions to REFUNDED, inventory is restored.
 * On rejection (approve=false): return status set to REJECTED, order unchanged.
 *
 * Returns 409 if return is not in REQUESTED status.
 * Returns 409 if refundAmount exceeds order.grandTotal (on approval).
 */
export const adminReviewReturn = async (
  returnId: number,
  payload: AdminReviewReturnPayload,
): Promise<ReturnRequest> => {
  const { data } = await apiClient.put<ApiResponse<ReturnRequest>>(
    `/admin/returns/${returnId}/review`,
    payload,
  );
  return data.data;
};
