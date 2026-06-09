/**
 * catalog.api.ts
 *
 * Public (storefront) catalog API calls.
 */

import apiClient from './client';
import type { ApiResponse } from '@/types/api.types';
import type {
  CategoryResponse,
  CategoryTreeResponse,
  ProductSummaryResponse,
  ProductDetailResponse,
  SpringPage,
} from '@/types/catalog.types';
import type { ImageResponse } from '@/types/image.types';

/**
 * Returns the full 3-level navigation tree: ROOT → GROUP → LEAF.
 * Used by the mega-menu and mobile navigation.
 */
export const getNavigationTree = async (): Promise<CategoryTreeResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<CategoryTreeResponse[]>>('/categories');
  return data.data;
};

/**
 * Returns a flat list of all active LEAF categories (depth=2).
 * Used by the admin product creation/edit form for the category picker.
 * Products may ONLY be assigned to LEAF categories.
 */
export const getLeafCategories = async (): Promise<CategoryResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<CategoryResponse[]>>('/categories/leaves');
  return data.data;
};

/**
 * Returns the canonical breadcrumb path for a category by its slug.
 * Returns up to 3 items: ROOT → GROUP → LEAF (or fewer for higher-level categories).
 */
export const getCategoryBreadcrumbs = async (slug: string): Promise<CategoryResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<CategoryResponse[]>>(
    `/categories/${slug}/breadcrumbs`
  );
  return data.data;
};

export const getProducts = async (page = 0, size = 24, sort?: string): Promise<SpringPage<ProductSummaryResponse>> => {
  const { data } = await apiClient.get<ApiResponse<SpringPage<ProductSummaryResponse>>>(
    '/products',
    { params: { page, size, ...(sort && { sort }) } },
  );
  return data.data;
};

export const getProductsByCategory = async (
  categorySlug: string,
  page = 0,
  size = 24,
  sort?: string
): Promise<SpringPage<ProductSummaryResponse>> => {
  const { data } = await apiClient.get<ApiResponse<SpringPage<ProductSummaryResponse>>>(
    `/products/category/${categorySlug}`,
    { params: { page, size, ...(sort && { sort }) } },
  );
  return data.data;
};

export const getProductBySlug = async (slug: string): Promise<ProductDetailResponse> => {
  const { data } = await apiClient.get<ApiResponse<ProductDetailResponse>>(`/products/${slug}`);
  return data.data;
};

export const getProductImages = async (productId: number): Promise<ImageResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<ImageResponse[]>>(`/products/${productId}/images`);
  return data.data;
};

export const getVariantImages = async (productId: number, variantId: number): Promise<ImageResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<ImageResponse[]>>(
    `/products/${productId}/variants/${variantId}/images`,
  );
  return data.data;
};
