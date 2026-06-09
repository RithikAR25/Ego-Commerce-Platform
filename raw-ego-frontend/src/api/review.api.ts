import apiClient from './client';
import type {
  ProductReview,
  ProductRatingSummary,
  CreateReviewPayload,
  ReviewPageResponse
} from '@/types/review.types';

import type { ApiResponse } from '@/types/api.types';

export const getProductReviews = async (productId: number, page: number = 0, size: number = 10): Promise<ReviewPageResponse> => {
  const { data } = await apiClient.get<ApiResponse<ReviewPageResponse>>(`/products/${productId}/reviews`, {
    params: { page, size, sort: 'createdAt,desc' }
  });
  return data.data;
};

export const getProductRatingSummary = async (productId: number): Promise<ProductRatingSummary> => {
  const { data } = await apiClient.get<ApiResponse<ProductRatingSummary>>(`/products/${productId}/reviews/summary`);
  return data.data;
};

export const submitReview = async (productId: number, payload: CreateReviewPayload): Promise<ProductReview> => {
  const { data } = await apiClient.post<ApiResponse<ProductReview>>(`/products/${productId}/reviews`, payload);
  return data.data;
};
