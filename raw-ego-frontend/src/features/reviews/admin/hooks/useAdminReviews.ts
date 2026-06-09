import { useMutation, useQueryClient } from '@tanstack/react-query';
import { adminDeleteReview } from '@/api/admin.review.api';
import { reviewKeys } from '@/features/reviews/hooks/useReviews'; // assume we'll use this

export const useAdminDeleteReview = (productId: number) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (reviewId: number) => adminDeleteReview(reviewId),
    onSuccess: () => {
      // Invalidate the public reviews query so the list updates
      queryClient.invalidateQueries({ queryKey: reviewKeys.lists(productId) });
      queryClient.invalidateQueries({ queryKey: reviewKeys.summary(productId) });
    },
  });
};
