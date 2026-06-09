import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SearchHistoryState {
  searches: string[];
  addSearch: (query: string) => void;
  removeSearch: (query: string) => void;
  clearHistory: () => void;
}

export const useSearchHistoryStore = create<SearchHistoryState>()(
  persist(
    (set) => ({
      searches: [],

      addSearch: (query) => set((state) => {
        const trimmed = query.trim();
        if (!trimmed) return state;

        // Deduplicate case-insensitively and keep the latest
        const filtered = state.searches.filter(
          (s) => s.toLowerCase() !== trimmed.toLowerCase()
        );

        // Max 10 searches, newest first
        return { searches: [trimmed, ...filtered].slice(0, 10) };
      }),

      removeSearch: (query) => set((state) => ({
        searches: state.searches.filter(
          (s) => s.toLowerCase() !== query.toLowerCase()
        )
      })),

      clearHistory: () => set({ searches: [] }),
    }),
    {
      name: 'ego_search_history',
    }
  )
);
