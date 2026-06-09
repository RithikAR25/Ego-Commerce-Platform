import apiClient from './client';
import type { WishlistResponse, AddToWishlistPayload } from '@/types/wishlist.types';

import type { ApiResponse } from '@/types/api.types';

export const getWishlist = async (): Promise<WishlistResponse> => {
  const { data } = await apiClient.get<ApiResponse<WishlistResponse>>('/wishlist');
  return data.data;
};

export const addToWishlist = async (payload: AddToWishlistPayload): Promise<void> => {
  await apiClient.post('/wishlist/items', payload);
};

export const removeFromWishlist = async (variantId: number): Promise<void> => {
  await apiClient.delete(`/wishlist/items/${variantId}`);
};

export const clearWishlist = async (): Promise<void> => {
  await apiClient.delete('/wishlist');
};
