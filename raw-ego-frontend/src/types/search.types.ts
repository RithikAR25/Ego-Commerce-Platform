/**
 * TypeScript interfaces for the Elasticsearch search API.
 * Mirrors the backend FacetedSearchResponse and SearchRequest DTOs.
 */

import type { ProductSummaryResponse } from './catalog.types';

// ── Request ──────────────────────────────────────────────────────────────────

export interface SearchParams {
  query?: string;
  /**
   * Category filter by slug — works at any depth (ROOT, GROUP, or LEAF).
   * Example: "men" filters all MEN products; "topwear" filters all Topwear;
   * "t-shirts" filters exactly T-Shirts.
   */
  categorySlug?: string;
  sizes?: string[];
  colors?: string[];
  minPrice?: number;
  maxPrice?: number;
  sort?: 'createdAt,desc' | 'minPrice,asc' | 'minPrice,desc' | 'avgRating,desc';
  page?: number;
  size?: number;
}

// ── Response ─────────────────────────────────────────────────────────────────

export interface FacetBucket {
  value: string;
  count: number;
}

export interface PriceStats {
  min: number;
  max: number;
  avg: number;
}

export interface SearchFacets {
  sizes: FacetBucket[];
  colors: FacetBucket[];
  priceStats: PriceStats;
}

export interface FacetedSearchResponse {
  content: ProductSummaryResponse[];
  totalElements: number;
  totalPages: number;
  facets: SearchFacets;
  /**
   * True when Elasticsearch was unavailable and results came from MySQL.
   * Frontend should show a "Limited search available" banner.
   */
  fallbackMode: boolean;
}
