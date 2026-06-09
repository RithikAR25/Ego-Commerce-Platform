/**
 * search.api.ts
 *
 * API client functions for the Elasticsearch-powered search endpoints.
 * Uses the shared Axios instance with JWT auth interceptors.
 */

import apiClient from './client';
import type { ApiResponse } from '@/types/api.types';
import type { FacetedSearchResponse, SearchParams } from '@/types/search.types';

/**
 * Executes a faceted product search.
 * Supports full-text query, category slug filter (any depth), size/color facets, and price range.
 * Falls back to MySQL results on ES failure (fallbackMode: true in response).
 *
 * @param params.categorySlug - Optional. Pass any slug at ROOT/GROUP/LEAF depth.
 *   e.g. "men" → all MEN products, "topwear" → all Topwear, "t-shirts" → exact leaf.
 */
export const searchProducts = async (params: SearchParams): Promise<FacetedSearchResponse> => {
  const { data } = await apiClient.get<ApiResponse<FacetedSearchResponse>>('/search', {
    params: {
      ...params,
      // Axios serialises arrays as sizes[]=M by default — override to sizes=M,L
      sizes: params.sizes?.join(',') || undefined,
      colors: params.colors?.join(',') || undefined,
    },
  });
  return data.data;
};

/**
 * Returns up to 5 product name suggestions for the given prefix.
 * Minimum 2 characters required. Returns empty array on ES failure.
 */
export const autocomplete = async (q: string): Promise<string[]> => {
  const { data } = await apiClient.get<ApiResponse<string[]>>('/search/autocomplete', {
    params: { q },
  });
  return data.data;
};

/**
 * [Admin] Triggers a full Elasticsearch reindex of all active products.
 * Requires ADMIN role.
 */
export const adminTriggerReindex = async (): Promise<string> => {
  const { data } = await apiClient.post<ApiResponse<string>>('/admin/search/reindex');
  return data.message ?? 'Reindex triggered.';
};
