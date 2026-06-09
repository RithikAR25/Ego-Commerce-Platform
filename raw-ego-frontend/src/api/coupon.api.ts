/**
 * coupon.api.ts
 *
 * API functions for the coupon domain.
 * Backend: com.ego.raw_ego.coupon (Phase 9)
 *
 * Public endpoint (no auth required):
 *   GET /api/v1/coupons/validate?code=X&subtotal=Y
 *
 * Zero side effects — only validates; does NOT apply or increment usage.
 * Actual coupon application happens atomically inside checkout().
 */

import apiClient from './client';
import type { ApiResponse } from '@/types/api.types';

// ── Response types ─────────────────────────────────────────────────────────────

/**
 * Matches the backend CouponValidateResponse DTO.
 * Returned by GET /api/v1/coupons/validate.
 */
export interface CouponValidateResponse {
  /** True when the coupon is valid, active, and applicable to this order. */
  valid:          boolean;
  /** Coupon code (normalized UPPER) */
  code:           string;
  /** Discount type: 'FLAT' | 'PERCENTAGE' */
  discountType:   'FLAT' | 'PERCENTAGE';
  /** Computed discount amount in INR for the given subtotal */
  discountAmount: number;
  /** Human-readable description — e.g. "20% off (capped at ₹500)" */
  description:    string;
}

// ── API function ───────────────────────────────────────────────────────────────

/**
 * Preview a coupon code discount against an order subtotal.
 *
 * This is a zero-side-effect read — it never applies the coupon or
 * increments usage counters. Actual application happens in checkout().
 *
 * @param code     coupon code to validate (case-insensitive)
 * @param subtotal order subtotal in INR (used to compute percentage discounts)
 */
export const validateCoupon = async (
  code: string,
  subtotal: number,
): Promise<CouponValidateResponse> => {
  const { data } = await apiClient.get<ApiResponse<CouponValidateResponse>>(
    '/coupons/validate',
    { params: { code: code.trim().toUpperCase(), subtotal } },
  );
  return data.data;
};
