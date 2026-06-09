export type DiscountType = 'FLAT' | 'PERCENTAGE';

export interface CouponResponse {
  id: number;
  code: string;
  description: string | null;
  discountType: DiscountType;
  discountValue: number;
  maxDiscountAmount: number | null;
  minOrderAmount: number | null;
  maxUses: number | null;
  currentUses: number;
  active: boolean;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCouponRequest {
  code: string;
  description?: string;
  discountType: DiscountType;
  discountValue: number;
  maxDiscountAmount?: number;
  minOrderAmount?: number;
  maxUses?: number;
  expiresAt?: string;
}

export interface UpdateCouponRequest {
  description?: string;
  maxDiscountAmount?: number | null;
  minOrderAmount?: number | null;
  maxUses?: number | null;
  expiresAt?: string | null;
}

export interface CouponValidationResponse {
  couponId: number;
  code: string;
  discountType: DiscountType;
  discountValue: number;
  discountAmountApplied: number;
}
