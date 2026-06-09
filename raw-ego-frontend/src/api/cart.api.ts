/**
 * cart.api.ts
 *
 * All HTTP calls for the cart domain.
 * Routes: /api/v1/cart/**  (all require authentication)
 */

import apiClient from './client';
import type { ApiResponse } from '@/types/api.types';
import type {
  CartResponse,
  AddToCartPayload,
  UpdateCartItemPayload,
  MergeCartPayload,
} from '@/types/cart.types';

// ── GET /api/v1/cart ──────────────────────────────────────────────────────────

export const getCart = async (): Promise<CartResponse> => {
  const { data } = await apiClient.get<ApiResponse<CartResponse>>('/cart');
  return data.data;
};

// ── POST /api/v1/cart/add ─────────────────────────────────────────────────────

export const addToCart = async (payload: AddToCartPayload): Promise<CartResponse> => {
  const { data } = await apiClient.post<ApiResponse<CartResponse>>('/cart/add', payload);
  return data.data;
};

// ── PUT /api/v1/cart/items/{variantId} ────────────────────────────────────────

export const updateCartItem = async (
  variantId: number,
  payload: UpdateCartItemPayload,
): Promise<CartResponse> => {
  const { data } = await apiClient.put<ApiResponse<CartResponse>>(
    `/cart/items/${variantId}`,
    payload,
  );
  return data.data;
};

// ── DELETE /api/v1/cart/items/{variantId} ─────────────────────────────────────

export const removeCartItem = async (variantId: number): Promise<CartResponse> => {
  const { data } = await apiClient.delete<ApiResponse<CartResponse>>(
    `/cart/items/${variantId}`,
  );
  return data.data;
};

// ── DELETE /api/v1/cart ───────────────────────────────────────────────────────

export const clearCart = async (): Promise<void> => {
  await apiClient.delete('/cart');
};

// ── POST /api/v1/cart/merge ───────────────────────────────────────────────────

export const mergeCart = async (payload: MergeCartPayload): Promise<CartResponse> => {
  const { data } = await apiClient.post<ApiResponse<CartResponse>>('/cart/merge', payload);
  return data.data;
};
