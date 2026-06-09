/**
 * useCatalog.ts
 *
 * Public (storefront) TanStack Query hooks for the catalog domain.
 */

import { useQuery } from '@tanstack/react-query';
import { categoryKeys, productKeys, imageKeys } from './queryKeys';
import {
  getNavigationTree,
  getLeafCategories,
  getCategoryBreadcrumbs,
  getProducts,
  getProductsByCategory,
  getProductBySlug,
  getProductImages,
  getVariantImages,
} from '@/api/catalog.api';

/**
 * Fetches the full 3-level navigation tree (ROOT → GROUP → LEAF).
 * Used by the Navbar mega-menu and mobile navigation.
 * Stale after 5 minutes (category tree rarely changes).
 */
export const useNavigationTree = () =>
  useQuery({
    queryKey:  categoryKeys.tree(),
    queryFn:   getNavigationTree,
    staleTime: 5 * 60 * 1000,
  });

/**
 * Fetches all active LEAF categories (flat list).
 * Used by the admin product creation/edit form for the category picker.
 * Products may ONLY be assigned to LEAF categories.
 */
export const useLeafCategories = () =>
  useQuery({
    queryKey:  [...categoryKeys.all(), 'leaves'],
    queryFn:   getLeafCategories,
    staleTime: 5 * 60 * 1000,
  });

/**
 * Fetches the canonical breadcrumb trail for a category slug.
 * Returns up to 3 items: ROOT → GROUP → LEAF.
 * Used by ProductListingPage and ProductDetailPage.
 */
export const useCategoryBreadcrumbs = (slug: string) =>
  useQuery({
    queryKey:  [...categoryKeys.all(), 'breadcrumbs', slug],
    queryFn:   () => getCategoryBreadcrumbs(slug),
    enabled:   Boolean(slug),
    staleTime: 5 * 60 * 1000,
  });

export const useProducts = (page = 0, size = 24, sort?: string) =>
  useQuery({
    queryKey: [...productKeys.list({ page, size }), sort],
    queryFn:  () => getProducts(page, size, sort),
    staleTime: 60 * 1000,
  });

export const useProductsByCategory = (categorySlug: string, page = 0, size = 24, sort?: string) =>
  useQuery({
    queryKey: [...productKeys.list({ categorySlug, page, size }), sort],
    queryFn:  () => getProductsByCategory(categorySlug, page, size, sort),
    enabled:  Boolean(categorySlug),
    staleTime: 60 * 1000,
  });

export const useProductDetail = (slug: string) =>
  useQuery({
    queryKey: productKeys.detail(slug),
    queryFn:  () => getProductBySlug(slug),
    enabled:  Boolean(slug),
    staleTime: 2 * 60 * 1000,
  });

export const useProductImages = (productId: number) =>
  useQuery({
    queryKey: imageKeys.gallery(productId),
    queryFn:  () => getProductImages(productId),
    enabled:  productId > 0,
    staleTime: 2 * 60 * 1000,
  });

export const useVariantImages = (productId: number, variantId: number) =>
  useQuery({
    queryKey: imageKeys.variant(productId, variantId),
    queryFn:  () => getVariantImages(productId, variantId),
    enabled:  productId > 0 && variantId > 0,
    staleTime: 2 * 60 * 1000,
  });
