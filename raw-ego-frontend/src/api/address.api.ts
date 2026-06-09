/**
 * address.api.ts
 *
 * Address book API — thin wrappers for all /api/v1/addresses endpoints.
 * All endpoints require a valid Bearer token (JWT-gated).
 */

import apiClient from '@/api/client';
import type { ApiResponse } from '@/types/api.types';

// ── Types ─────────────────────────────────────────────────────────────────────

export type AddressType = 'HOME' | 'WORK' | 'OTHER';

export interface UserAddress {
  id: number;
  fullName: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  landmark?: string;
  city: string;
  state: string;
  pinCode: string;
  country: string;
  addressType: AddressType;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AddressRequest {
  fullName: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  landmark?: string;
  city: string;
  state: string;
  pinCode: string;
  country?: string;
  addressType?: AddressType;
  setAsDefault?: boolean;
}

// ── API functions ─────────────────────────────────────────────────────────────

/** List all active addresses for the authenticated user, default-first. */
export const listAddresses = () =>
  apiClient.get<ApiResponse<UserAddress[]>>('/addresses');

/** Get a single address by ID. */
export const getAddress = (id: number) =>
  apiClient.get<ApiResponse<UserAddress>>(`/addresses/${id}`);

/** Add a new address. Returns 409 if user already has 5 addresses. */
export const addAddress = (body: AddressRequest) =>
  apiClient.post<ApiResponse<UserAddress>>('/addresses', body);

/** Full update (replace all fields) of an existing address. */
export const updateAddress = (id: number, body: AddressRequest) =>
  apiClient.put<ApiResponse<UserAddress>>(`/addresses/${id}`, body);

/** Soft-delete an address. The record is retained for order history. */
export const deleteAddress = (id: number) =>
  apiClient.delete<ApiResponse<null>>(`/addresses/${id}`);

/** Set the specified address as the default. Clears all other defaults atomically. */
export const setDefaultAddress = (id: number) =>
  apiClient.patch<ApiResponse<UserAddress>>(`/addresses/${id}/default`);
