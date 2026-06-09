import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getProductReviews, getProductRatingSummary, submitReview } from '@/api/review.api';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/uiStore';
import type { CreateReviewPayload } from '@/types/review.types';

export const reviewKeys = {
  all:     (productId: number) => ['reviews', productId] as const,
  lists:   (productId: number) => [...reviewKeys.all(productId), 'list'] as const,
  list:    (productId: number, page: number) => [...reviewKeys.lists(productId), page] as const,
  summary: (productId: number) => [...reviewKeys.all(productId), 'summary'] as const,
};

export const useProductReviews = (productId: number, page: number = 0, size: number = 10) => {
  return useQuery({
    queryKey: reviewKeys.list(productId, page),
    queryFn:  () => getProductReviews(productId, page, size),
    enabled:  !!productId,
    staleTime: 5 * 60 * 1000,
  });
};

export const useProductRatingSummary = (productId: number) => {
  return useQuery({
    queryKey: reviewKeys.summary(productId),
    queryFn:  () => getProductRatingSummary(productId),
    enabled:  !!productId,
    staleTime: 5 * 60 * 1000,
  });
};

export const useSubmitReview = (productId: number) => {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();

  return useMutation({
    mutationFn: async (payload: CreateReviewPayload) => {
      if (!isAuthenticated) {
        throw new Error('Please login to submit a review.');
      }
      return submitReview(productId, payload);
    },
    onSuccess: () => {
      toast.success('Review submitted successfully!');
      // Invalidate both summary and lists
      queryClient.invalidateQueries({ queryKey: reviewKeys.all(productId) });
    },
    onError: (error: any) => {
      // Typically 403 if they haven't purchased it or 409 if already reviewed
      const msg = error.response?.data?.message || 'Could not submit review.';
      if (error.response?.status === 403) {
        toast.error('You can only review products you have purchased and received.');
      } else {
        toast.error(msg);
      }
    },
  });
};
