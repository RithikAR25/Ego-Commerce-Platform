/**
 * useCart.ts
 *
 * TanStack Query hooks for all cart operations.
 *
 * Architecture:
 * - useCart()           → useQuery  — fetches the full cart (items, subtotal, count)
 * - useAddToCart()      → useMutation — adds a variant, invalidates cart cache
 * - useUpdateCartItem() → useMutation — updates quantity, invalidates cart cache
 * - useRemoveCartItem() → useMutation — removes a line item, invalidates cart cache
 * - useClearCart()      → useMutation — clears all items, invalidates cart cache
 * - useMergeCart()      → useMutation — called on login to merge anonymous cart
 *
 * All mutations sync cartStore.itemCount on success so the Navbar badge
 * stays up-to-date without requiring a separate polling mechanism.
 *
 * Rule: never call cart API functions directly in useEffect or components.
 * Always use these hooks.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getCart,
  addToCart,
  updateCartItem,
  removeCartItem,
  clearCart,
  mergeCart,
} from '@/api/cart.api';
import type {
  AddToCartPayload,
  UpdateCartItemPayload,
  MergeCartPayload,
} from '@/types/cart.types';
import { useCartStore } from '@/store/cartStore';
import { toast } from '@/store/uiStore';
import { useAuthStore } from '@/store/authStore';

// ── Query key factory ─────────────────────────────────────────────────────────

export const cartKeys = {
  all:  ['cart'] as const,
  cart: () => [...cartKeys.all] as const,
};

// ── useCart ───────────────────────────────────────────────────────────────────

/**
 * Fetches the current authenticated user's cart.
 * Only enabled when the user is authenticated.
 */
export const useCart = () => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const setItemCount    = useCartStore((s) => s.setItemCount);

  return useQuery({
    queryKey: cartKeys.cart(),
    queryFn:  getCart,
    enabled:  isAuthenticated,
    // Keep cart data fresh for 30s — short enough to catch stock changes
    staleTime: 30_000,
    // Sync badge count whenever the query resolves
    select: (data) => {
      setItemCount(data.itemCount);
      return data;
    },
  });
};

// ── useAddToCart ──────────────────────────────────────────────────────────────

export const useAddToCart = (options?: { suppressToast?: boolean }) => {
  const queryClient  = useQueryClient();
  const setItemCount = useCartStore((s) => s.setItemCount);

  return useMutation({
    mutationFn: (payload: AddToCartPayload) => addToCart(payload),
    onSuccess: (cart) => {
      queryClient.setQueryData(cartKeys.cart(), cart);
      setItemCount(cart.itemCount);
      if (!options?.suppressToast) {
        toast.success('Added to cart!');
      }
    },
    onError: (error: { response?: { data?: { message?: string } } }) => {
      const message = error.response?.data?.message ?? 'Could not add to cart.';
      toast.error(message);
    },
  });
};

// ── useUpdateCartItem ─────────────────────────────────────────────────────────

export const useUpdateCartItem = () => {
  const queryClient  = useQueryClient();
  const setItemCount = useCartStore((s) => s.setItemCount);

  return useMutation({
    mutationFn: ({ variantId, payload }: { variantId: number; payload: UpdateCartItemPayload }) =>
      updateCartItem(variantId, payload),
    onSuccess: (cart) => {
      queryClient.setQueryData(cartKeys.cart(), cart);
      setItemCount(cart.itemCount);
    },
    onError: () => {
      toast.error('Could not update cart.');
      // Invalidate to re-sync with server truth
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
};

// ── useRemoveCartItem ─────────────────────────────────────────────────────────

export const useRemoveCartItem = () => {
  const queryClient  = useQueryClient();
  const setItemCount = useCartStore((s) => s.setItemCount);

  return useMutation({
    mutationFn: (variantId: number) => removeCartItem(variantId),
    onSuccess: (cart) => {
      queryClient.setQueryData(cartKeys.cart(), cart);
      setItemCount(cart.itemCount);
      toast.success('Item removed from cart.');
    },
    onError: () => {
      toast.error('Could not remove item.');
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
};

// ── useClearCart ──────────────────────────────────────────────────────────────

export const useClearCart = () => {
  const queryClient  = useQueryClient();
  const resetCart    = useCartStore((s) => s.resetCart);

  return useMutation({
    mutationFn: clearCart,
    onSuccess: () => {
      queryClient.setQueryData(cartKeys.cart(), {
        items: [], itemCount: 0, subtotal: 0,
      });
      resetCart();
    },
    onError: () => {
      toast.error('Could not clear cart.');
    },
  });
};

// ── useMergeCart ──────────────────────────────────────────────────────────────

/**
 * Merges the anonymous session cart into the authenticated user's cart.
 * Called once immediately after a successful login.
 */
export const useMergeCart = () => {
  const queryClient  = useQueryClient();
  const setItemCount = useCartStore((s) => s.setItemCount);

  return useMutation({
    mutationFn: (payload: MergeCartPayload) => mergeCart(payload),
    onSuccess: (cart) => {
      queryClient.setQueryData(cartKeys.cart(), cart);
      setItemCount(cart.itemCount);
    },
    onError: () => {
      // Merge failure is non-critical — refetch the user cart instead
      queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
};
