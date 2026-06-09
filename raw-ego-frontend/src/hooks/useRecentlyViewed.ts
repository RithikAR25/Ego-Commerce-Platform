import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface ViewedProduct {
  id: number;
  slug: string;
  name: string;
  price: number;
  compareAtPrice?: number | null;
  imageUrl?: string;
  viewedAt: number;
}

interface RecentlyViewedState {
  items: ViewedProduct[];
  addViewedItem: (item: Omit<ViewedProduct, 'viewedAt'>) => void;
  clearHistory: () => void;
}

const MAX_ITEMS = 10;

export const useRecentlyViewed = create<RecentlyViewedState>()(
  persist(
    (set) => ({
      items: [],
      addViewedItem: (newItem) => set((state) => {
        // Remove item if it already exists to move it to the front
        const filtered = state.items.filter((i) => i.id !== newItem.id);
        const itemWithTimestamp = { ...newItem, viewedAt: Date.now() };
        
        // Add to front, slice to max
        return {
          items: [itemWithTimestamp, ...filtered].slice(0, MAX_ITEMS),
        };
      }),
      clearHistory: () => set({ items: [] }),
    }),
    {
      name: 'ego_recently_viewed',
    }
  )
);
