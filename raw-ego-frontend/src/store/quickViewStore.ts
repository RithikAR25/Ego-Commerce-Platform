/**
 * quickViewStore.ts
 *
 * Zustand store for managing the global Quick View modal state.
 */

import { create } from 'zustand';

export interface QuickViewState {
  isOpen: boolean;
  isLoading: boolean;
  productSlug: string | null;
  openQuickView: (slug: string) => void;
  closeQuickView: () => void;
  setLoading: (loading: boolean) => void;
}

export const useQuickViewStore = create<QuickViewState>()((set) => ({
  isOpen: false,
  isLoading: false,
  productSlug: null,
  openQuickView: (slug) => set({ isOpen: true, productSlug: slug, isLoading: true }),
  closeQuickView: () => set({ isOpen: false, productSlug: null, isLoading: false }),
  setLoading: (loading) => set({ isLoading: loading }),
}));
