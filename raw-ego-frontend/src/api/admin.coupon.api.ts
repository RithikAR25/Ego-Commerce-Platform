import apiClient from './client';
import type { PaginatedResponse, ApiResponse } from '@/types/api.types';
import type { 
  CouponResponse, 
  CreateCouponRequest, 
  UpdateCouponRequest 
} from '@/types/coupon.types';

export const adminListCoupons = async (page = 0, size = 20): Promise<PaginatedResponse<CouponResponse>> => {
  const { data } = await apiClient.get<ApiResponse<PaginatedResponse<CouponResponse>>>('/admin/coupons', {
    params: { page, size },
  });
  return data.data;
};

export const adminGetCoupon = async (id: number): Promise<CouponResponse> => {
  const { data } = await apiClient.get<ApiResponse<CouponResponse>>(`/admin/coupons/${id}`);
  return data.data;
};

export const adminCreateCoupon = async (payload: CreateCouponRequest): Promise<CouponResponse> => {
  const { data } = await apiClient.post<ApiResponse<CouponResponse>>('/admin/coupons', payload);
  return data.data;
};

export const adminUpdateCoupon = async (id: number, payload: UpdateCouponRequest): Promise<CouponResponse> => {
  const { data } = await apiClient.put<ApiResponse<CouponResponse>>(`/admin/coupons/${id}`, payload);
  return data.data;
};

export const adminDeactivateCoupon = async (id: number): Promise<void> => {
  await apiClient.delete(`/admin/coupons/${id}`);
};
