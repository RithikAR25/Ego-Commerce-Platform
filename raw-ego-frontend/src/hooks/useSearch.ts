/**
 * useSearch.ts
 *
 * TanStack Query hooks for Elasticsearch-powered search and autocomplete.
 */

import { useQuery, useMutation } from '@tanstack/react-query';
import { searchProducts, autocomplete, adminTriggerReindex } from '@/api/search.api';
import type { SearchParams } from '@/types/search.types';

// ── Query keys ───────────────────────────────────────────────────────────────

export const searchKeys = {
  all:          ['search'] as const,
  results:      (params: SearchParams) => ['search', 'results', params] as const,
  autocomplete: (q: string)            => ['search', 'autocomplete', q] as const,
};

// ── Hooks ─────────────────────────────────────────────────────────────────────

/**
 * Executes a faceted product search.
 *
 * @param params - Search parameters (query, filters, sort, pagination)
 * @param enabled - Set to false to pause the query (e.g. while user is typing)
 */
export const useSearch = (params: SearchParams, enabled = true) => {
  return useQuery({
    queryKey:  searchKeys.results(params),
    queryFn:   () => searchProducts(params),
    enabled,
    staleTime: 30_000,   // 30s — search results don't need to be ultra-fresh
    placeholderData: (prev) => prev,  // keep previous page visible during navigation
  });
};

/**
 * Returns autocomplete suggestions for a search query prefix.
 * Only fires when the query is at least 2 characters.
 *
 * @param q - The search prefix typed by the user (debounced before being passed here)
 */
export const useAutocomplete = (q: string) => {
  return useQuery({
    queryKey:  searchKeys.autocomplete(q),
    queryFn:   () => autocomplete(q),
    enabled:   q.trim().length >= 2,
    staleTime: 30_000,
    gcTime:    60_000,
  });
};

/**
 * [Admin] Triggers a full Elasticsearch reindex of all active products.
 */
export const useReindex = () => {
  return useMutation({
    mutationFn: () => adminTriggerReindex(),
  });
};
