import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getWishlist, addToWishlist, removeFromWishlist, clearWishlist } from '@/api/wishlist.api';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/uiStore';
import type { WishlistResponse, AddToWishlistPayload } from '@/types/wishlist.types';

export const wishlistKeys = {
  all:     ['wishlist'] as const,
  details: () => [...wishlistKeys.all, 'detail'] as const,
};

export const useWishlist = () => {
  const { isAuthenticated } = useAuthStore();

  return useQuery({
    queryKey: wishlistKeys.details(),
    queryFn:  getWishlist,
    enabled:  isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useAddToWishlist = () => {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();

  return useMutation({
    mutationFn: async (payload: AddToWishlistPayload) => {
      if (!isAuthenticated) {
        throw new Error('Please login to add items to your wishlist.');
      }
      return addToWishlist(payload);
    },
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: wishlistKeys.details() });
      const previousWishlist = queryClient.getQueryData<WishlistResponse>(wishlistKeys.details());

      // Optimistically we just add a dummy item if not loaded, but usually we just invalidate
      return { previousWishlist };
    },
    onSuccess: () => {
      toast.success('Added to wishlist');
      queryClient.invalidateQueries({ queryKey: wishlistKeys.details() });
    },
    onError: (error: any, _payload, context) => {
      if (context?.previousWishlist) {
        queryClient.setQueryData(wishlistKeys.details(), context.previousWishlist);
      }
      toast.error(error.response?.data?.message || error.message || 'Could not add to wishlist');
    },
  });
};

export const useRemoveFromWishlist = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: removeFromWishlist,
    onMutate: async (variantId) => {
      await queryClient.cancelQueries({ queryKey: wishlistKeys.details() });
      const previousWishlist = queryClient.getQueryData<WishlistResponse>(wishlistKeys.details());

      if (previousWishlist) {
        queryClient.setQueryData<WishlistResponse>(wishlistKeys.details(), {
          ...previousWishlist,
          items: previousWishlist.items.filter((item) => item.variantId !== variantId),
        });
      }

      return { previousWishlist };
    },
    onSuccess: () => {
      toast.success('Removed from wishlist');
    },
    onError: (error: any, _, context) => {
      if (context?.previousWishlist) {
        queryClient.setQueryData(wishlistKeys.details(), context.previousWishlist);
      }
      toast.error(error.response?.data?.message || 'Could not remove from wishlist');
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: wishlistKeys.details() });
    },
  });
};

export const useClearWishlist = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: clearWishlist,
    onSuccess: () => {
      queryClient.setQueryData<WishlistResponse>(wishlistKeys.details(), { items: [] });
      toast.success('Wishlist cleared');
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.message || 'Could not clear wishlist');
    },
  });
};
