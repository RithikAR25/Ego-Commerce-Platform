/**
 * order.api.ts
 *
 * All HTTP calls for the order domain.
 *
 * Customer routes: /api/v1/orders/**       (require authentication)
 * Admin routes:    /api/v1/admin/orders/** (require ROLE_ADMIN)
 */

import apiClient from './client';
import type { ApiResponse, PaginatedResponse } from '@/types/api.types';
import type {
  OrderDetail,
  OrderSummary,
  CheckoutPayload,
  UpdateOrderStatusPayload,
  OrderStatus,
} from '@/types/order.types';

// ── POST /api/v1/orders/checkout ──────────────────────────────────────────────

/**
 * Converts the current cart into a persisted order.
 * Returns the full order detail on success (201 Created).
 * Throws 409 if cart is empty or any item is out of stock.
 */
export const checkout = async (payload: CheckoutPayload): Promise<OrderDetail> => {
  const { data } = await apiClient.post<ApiResponse<OrderDetail>>('/orders/checkout', payload);
  return data.data;
};

// ── GET /api/v1/orders ────────────────────────────────────────────────────────

/**
 * Returns the authenticated user's order history, newest first.
 * Paginated — use page and size params.
 */
export const getOrders = async (page = 0, size = 10): Promise<PaginatedResponse<OrderSummary>> => {
  const { data } = await apiClient.get<ApiResponse<PaginatedResponse<OrderSummary>>>(
    `/orders?page=${page}&size=${size}`,
  );
  return data.data;
};

// ── GET /api/v1/orders/{orderId} ──────────────────────────────────────────────

/**
 * Returns the full detail for a specific order.
 * Returns 404 if the order doesn't exist or belongs to another user.
 */
export const getOrder = async (orderId: number): Promise<OrderDetail> => {
  const { data } = await apiClient.get<ApiResponse<OrderDetail>>(`/orders/${orderId}`);
  return data.data;
};

// ── POST /api/v1/orders/{orderId}/cancel ──────────────────────────────────────

/**
 * Cancels a PENDING_PAYMENT order.
 * Returns 409 if the order has already been confirmed or beyond.
 */
export const cancelOrder = async (orderId: number): Promise<OrderDetail> => {
  const { data } = await apiClient.post<ApiResponse<OrderDetail>>(`/orders/${orderId}/cancel`);
  return data.data;
};

// ── GET /api/v1/admin/orders ──────────────────────────────────────────────────

/**
 * Admin: returns all orders, optionally filtered by status.
 * Pass status=undefined to return all orders.
 */
export const adminGetOrders = async (
  status?: OrderStatus,
  page = 0,
  size = 20,
): Promise<PaginatedResponse<OrderSummary>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.append('status', status);

  const { data } = await apiClient.get<ApiResponse<PaginatedResponse<OrderSummary>>>(
    `/admin/orders?${params.toString()}`,
  );
  return data.data;
};

// ── PUT /api/v1/admin/orders/{orderId}/status ─────────────────────────────────

/**
 * Admin: advances the order to the specified status.
 * Returns 400 if the transition is invalid per the state machine.
 */
export const adminUpdateOrderStatus = async (
  orderId: number,
  payload: UpdateOrderStatusPayload,
): Promise<OrderDetail> => {
  const { data } = await apiClient.put<ApiResponse<OrderDetail>>(
    `/admin/orders/${orderId}/status`,
    payload,
  );
  return data.data;
};

// ── GET /api/v1/admin/orders/{orderId} ────────────────────────────────────────

/**
 * Admin: returns full detail for any order by ID, bypassing ownership check.
 */
export const adminGetOrder = async (orderId: number): Promise<OrderDetail> => {
  const { data } = await apiClient.get<ApiResponse<OrderDetail>>(
    `/admin/orders/${orderId}`,
  );
  return data.data;
};
