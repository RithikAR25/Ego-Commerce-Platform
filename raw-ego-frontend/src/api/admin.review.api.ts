import apiClient from './client';

export const adminDeleteReview = async (reviewId: number): Promise<void> => {
  await apiClient.delete(`/admin/reviews/${reviewId}`);
};
