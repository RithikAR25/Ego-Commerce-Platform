/**
 * payment.api.ts
 *
 * API functions for the Phase 7 Razorpay payment integration.
 * All calls go through the standard authenticated apiClient.
 */

import apiClient from './client';
import type { ApiResponse } from '../types/api.types';
import type { CreatePaymentOrderPayload, PaymentOrderResponse } from '../types/order.types';

/**
 * Creates a Razorpay payment order for an existing PENDING_PAYMENT EGO order.
 *
 * @param payload - { orderId: number }
 * @returns PaymentOrderResponse containing razorpayOrderId, amount, currency, keyId, egoOrderId
 *
 * Endpoint: POST /api/v1/payments/razorpay/create
 * Auth: Bearer JWT (customer)
 */
export const createPaymentOrder = async (
  payload: CreatePaymentOrderPayload
): Promise<PaymentOrderResponse> => {
  const res = await apiClient.post<ApiResponse<PaymentOrderResponse>>(
    '/payments/razorpay/create',
    payload
  );
  return res.data.data;
};
