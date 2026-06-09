/**
 * admin.catalog.api.ts
 *
 * Admin-only catalog API calls.
 * All paths use the apiClient baseURL (/api/v1) prefix.
 */

import apiClient from './client';
import type { ApiResponse } from '@/types/api.types';
import type {
  CategoryResponse,
  CategoryHierarchyLinkResponse,
  ProductSummaryResponse,
  ProductDetailResponse,
  CreateProductPayload,
  CreateVariantPayload,
  UpdateVariantPayload,
  UpdateProductStatusPayload,
  SetInventoryPayload,
  CreateCategoryPayload,
  UpdateCategoryPayload,
  AddHierarchyLinkPayload,
  UpdateHierarchyLinkPayload,
  SpringPage,
  CreateAttributeTypePayload,
  CreateAttributeValuePayload,
  AttributeTypeDetailResponse,
  InventoryItemResponse,
  InventoryFilterParams,
} from '@/types/catalog.types';
import type { ImageResponse, ReorderItem } from '@/types/image.types';

// ── Categories ────────────────────────────────────────────────────────────────

export const adminGetAllCategories = async (): Promise<CategoryResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<CategoryResponse[]>>('/admin/categories');
  return data.data;
};

export const adminCreateCategory = async (payload: CreateCategoryPayload): Promise<CategoryResponse> => {
  const { data } = await apiClient.post<ApiResponse<CategoryResponse>>('/admin/categories', payload);
  return data.data;
};

/**
 * PATCH /api/v1/admin/categories/{id}
 * Updates category name, description, imageUrl, displayOrder, and optionally slug.
 * Code is NOT editable (immutable — baked into variant SKUs).
 */
export const adminUpdateCategory = async (id: number, payload: UpdateCategoryPayload): Promise<CategoryResponse> => {
  const { data } = await apiClient.patch<ApiResponse<CategoryResponse>>(`/admin/categories/${id}`, payload);
  return data.data;
};

export const adminDeactivateCategory = async (id: number): Promise<void> => {
  await apiClient.delete(`/admin/categories/${id}`);
};

/**
 * PATCH /api/v1/admin/categories/{id}/activate
 * Re-activates a soft-deactivated category, restoring it to the storefront.
 * Returns 409 Conflict if the category is already active.
 */
export const adminActivateCategory = async (id: number): Promise<void> => {
  await apiClient.patch(`/admin/categories/${id}/activate`);
};

/**
 * DELETE /api/v1/admin/categories/{id}/permanent
 * Permanently removes a category. Only succeeds when the category is already
 * inactive, has no products assigned, and (if root) has no subcategories.
 */
export const adminHardDeleteCategory = async (id: number): Promise<void> => {
  await apiClient.delete(`/admin/categories/${id}/permanent`);
};

// ── Hierarchy Link Management ─────────────────────────────────────────────────

/**
 * GET /api/v1/admin/categories/{childId}/parents
 * Lists all parent hierarchy links for a subcategory.
 */
export const adminGetParentLinks = async (childId: number): Promise<CategoryHierarchyLinkResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<CategoryHierarchyLinkResponse[]>>(
    `/admin/categories/${childId}/parents`,
  );
  return data.data;
};

/**
 * POST /api/v1/admin/categories/{childId}/parents
 * Adds a root category as an additional parent (cross-listing).
 * Idempotent: returns existing link if already exists.
 */
export const adminAddParentToCategory = async (
  childId: number,
  payload: AddHierarchyLinkPayload,
): Promise<CategoryHierarchyLinkResponse> => {
  const { data } = await apiClient.post<ApiResponse<CategoryHierarchyLinkResponse>>(
    `/admin/categories/${childId}/parents`,
    payload,
  );
  return data.data;
};

/**
 * PATCH /api/v1/admin/categories/{childId}/parents/{parentId}
 * Updates link metadata (displayOrder, visibility, navigationLabel).
 */
export const adminUpdateHierarchyLink = async (
  childId:  number,
  parentId: number,
  payload:  UpdateHierarchyLinkPayload,
): Promise<CategoryHierarchyLinkResponse> => {
  const { data } = await apiClient.patch<ApiResponse<CategoryHierarchyLinkResponse>>(
    `/admin/categories/${childId}/parents/${parentId}`,
    payload,
  );
  return data.data;
};

/**
 * DELETE /api/v1/admin/categories/{childId}/parents/{parentId}
 * Removes a cross-listing. Blocked if it would orphan the subcategory.
 */
export const adminRemoveParentFromCategory = async (
  childId:  number,
  parentId: number,
): Promise<void> => {
  await apiClient.delete(`/admin/categories/${childId}/parents/${parentId}`);
};

/**
 * PUT /api/v1/admin/categories/{childId}/parents/{parentId}/primary
 * Promotes a link to canonical primary parent (updates parent_id FK too).
 */
export const adminSetPrimaryParent = async (
  childId:  number,
  parentId: number,
): Promise<CategoryHierarchyLinkResponse> => {
  const { data } = await apiClient.put<ApiResponse<CategoryHierarchyLinkResponse>>(
    `/admin/categories/${childId}/parents/${parentId}/primary`,
  );
  return data.data;
};

/**
 * GET /api/v1/admin/categories/{parentId}/children
 * Lists all child links under a root category (for admin nav builder).
 */
export const adminGetChildLinks = async (parentId: number): Promise<CategoryHierarchyLinkResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<CategoryHierarchyLinkResponse[]>>(
    `/admin/categories/${parentId}/children`,
  );
  return data.data;
};

/**
 * PUT /api/v1/admin/categories/{parentId}/children/reorder
 * Reorders subcategories under a root by submitting an ordered array of child IDs.
 */
export const adminReorderCategoryChildren = async (
  parentId:       number,
  orderedChildIds: number[],
): Promise<CategoryHierarchyLinkResponse[]> => {
  const { data } = await apiClient.put<ApiResponse<CategoryHierarchyLinkResponse[]>>(
    `/admin/categories/${parentId}/children/reorder`,
    orderedChildIds,
  );
  return data.data;
};

// ── Products ──────────────────────────────────────────────────────────────────

export const adminGetProducts = async (page = 0, size = 20): Promise<SpringPage<ProductSummaryResponse>> => {
  const { data } = await apiClient.get<ApiResponse<SpringPage<ProductSummaryResponse>>>(
    '/admin/products',
    { params: { page, size } },
  );
  return data.data;
};

export const adminGetProductBySlug = async (slug: string): Promise<ProductDetailResponse> => {
  const { data } = await apiClient.get<ApiResponse<ProductDetailResponse>>(`/admin/products/${slug}`);
  return data.data;
};

export const adminCreateProduct = async (payload: CreateProductPayload): Promise<ProductDetailResponse> => {
  const { data } = await apiClient.post<ApiResponse<ProductDetailResponse>>('/admin/products', payload);
  return data.data;
};

export const adminUpdateProductStatus = async (productId: number, payload: UpdateProductStatusPayload): Promise<ProductDetailResponse> => {
  const { data } = await apiClient.patch<ApiResponse<ProductDetailResponse>>(
    `/admin/products/${productId}/status`,
    payload,
  );
  return data.data;
};

export const adminArchiveProduct = async (productId: number): Promise<void> => {
  await apiClient.delete(`/admin/products/${productId}`);
};

/**
 * DELETE /api/v1/admin/products/{id}/permanent
 *
 * Permanently removes the product and all related data.
 * Requires the product to be ARCHIVED and have no order history.
 * Returns 204 No Content on success, 409 Conflict if guards fail.
 */
export const adminHardDeleteProduct = async (productId: number): Promise<void> => {
  await apiClient.delete(`/admin/products/${productId}/permanent`);
};

// ── Attribute Types & Values ──────────────────────────────────────────────────

/**
 * GET /api/v1/admin/products/{productId}/attribute-types
 * Returns all attribute types (Color, Size) with their values for a product.
 */
export const adminGetAttributeTypes = async (productId: number): Promise<AttributeTypeDetailResponse[]> => {
  const { data } = await apiClient.get<ApiResponse<AttributeTypeDetailResponse[]>>(
    `/admin/products/${productId}/attribute-types`,
  );
  return data.data;
};

/**
 * POST /api/v1/admin/products/{productId}/attribute-types
 * Creates a new attribute type (e.g. "Color", "Size") for a product.
 */
export const adminCreateAttributeType = async (
  productId: number,
  payload: CreateAttributeTypePayload,
): Promise<AttributeTypeDetailResponse> => {
  const { data } = await apiClient.post<ApiResponse<AttributeTypeDetailResponse>>(
    `/admin/products/${productId}/attribute-types`,
    payload,
  );
  return data.data;
};

/**
 * POST /api/v1/admin/attribute-types/{typeId}/values
 * Adds an attribute value (e.g. "Black"/"BLK") to an existing attribute type.
 */
export const adminCreateAttributeValue = async (
  attributeTypeId: number,
  payload: CreateAttributeValuePayload,
): Promise<AttributeTypeDetailResponse> => {
  const { data } = await apiClient.post<ApiResponse<AttributeTypeDetailResponse>>(
    `/admin/attribute-types/${attributeTypeId}/values`,
    payload,
  );
  return data.data;
};

// ── Variants ──────────────────────────────────────────────────────────────────

export const adminCreateVariant = async (productId: number, payload: CreateVariantPayload) => {
  const { data } = await apiClient.post(`/admin/products/${productId}/variants`, payload);
  return data.data;
};

export const adminUpdateVariant = async (variantId: number, payload: UpdateVariantPayload) => {
  const { data } = await apiClient.put(`/admin/variants/${variantId}`, payload);
  return data.data;
};

// ── Inventory ─────────────────────────────────────────────────────────────────

/**
 * GET /api/v1/admin/inventory
 * Paginated inventory list — filterable by stockStatus and SKU/product-name search.
 */
export const adminGetInventory = async (
  params: InventoryFilterParams = {}
): Promise<SpringPage<InventoryItemResponse>> => {
  const { data } = await apiClient.get<ApiResponse<SpringPage<InventoryItemResponse>>>(
    '/admin/inventory',
    {
      params: {
        status:  params.status  || undefined,
        search:  params.search  || undefined,
        page:    params.page    ?? 0,
        size:    params.size    ?? 25,
        sortBy:  params.sortBy  ?? 'sku',
        sortDir: params.sortDir ?? 'asc',
      },
    }
  );
  return data.data;
};

/**
 * PUT /api/v1/admin/inventory/{variantId}
 * Sets absolute quantityAvailable. Optionally updates lowStockThreshold.
 * Field name is quantityAvailable — matches UpdateInventoryRequest.java exactly.
 */
export const adminSetInventory = async (
  variantId: number,
  payload: SetInventoryPayload
): Promise<InventoryItemResponse> => {
  const { data } = await apiClient.put<ApiResponse<InventoryItemResponse>>(
    `/admin/inventory/${variantId}`,
    payload
  );
  return data.data;
};

/**
 * PATCH /api/v1/admin/inventory/{variantId}/threshold
 * Updates ONLY the low-stock alert threshold — does not touch quantityAvailable.
 */
export const adminUpdateThreshold = async (
  variantId: number,
  lowStockThreshold: number
): Promise<InventoryItemResponse> => {
  const { data } = await apiClient.patch<ApiResponse<InventoryItemResponse>>(
    `/admin/inventory/${variantId}/threshold`,
    { lowStockThreshold }
  );
  return data.data;
};

// ── Product Gallery Images ────────────────────────────────────────────────────

export const adminUploadGalleryImage = async (productId: number, file: File, altText?: string, displayOrder?: number): Promise<ImageResponse> => {
  const form = new FormData();
  form.append('file', file);
  if (altText !== undefined) form.append('altText', altText);
  if (displayOrder !== undefined) form.append('displayOrder', String(displayOrder));
  const { data } = await apiClient.post<ApiResponse<ImageResponse>>(`/admin/products/${productId}/images`, form, { headers: { 'Content-Type': 'multipart/form-data' } });
  return data.data;
};

export const adminDeleteGalleryImage = async (productId: number, imageId: number): Promise<void> => {
  await apiClient.delete(`/admin/products/${productId}/images/${imageId}`);
};

export const adminReorderGalleryImages = async (productId: number, order: ReorderItem[]): Promise<ImageResponse[]> => {
  const { data } = await apiClient.put<ApiResponse<ImageResponse[]>>(`/admin/products/${productId}/images/reorder`, { order });
  return data.data;
};

// ── Variant Images ────────────────────────────────────────────────────────────

export const adminUploadVariantImage = async (productId: number, variantId: number, file: File, altText?: string, displayOrder?: number): Promise<ImageResponse> => {
  const form = new FormData();
  form.append('file', file);
  if (altText !== undefined) form.append('altText', altText);
  if (displayOrder !== undefined) form.append('displayOrder', String(displayOrder));
  const { data } = await apiClient.post<ApiResponse<ImageResponse>>(
    `/admin/products/${productId}/variants/${variantId}/images`,
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  );
  return data.data;
};

export const adminDeleteVariantImage = async (productId: number, variantId: number, imageId: number): Promise<void> => {
  await apiClient.delete(`/admin/products/${productId}/variants/${variantId}/images/${imageId}`);
};

export const adminSetPrimaryVariantImage = async (productId: number, variantId: number, imageId: number): Promise<ImageResponse> => {
  const { data } = await apiClient.patch<ApiResponse<ImageResponse>>(`/admin/products/${productId}/variants/${variantId}/images/${imageId}/primary`);
  return data.data;
};

export const adminReorderVariantImages = async (productId: number, variantId: number, order: ReorderItem[]): Promise<ImageResponse[]> => {
  const { data } = await apiClient.put<ApiResponse<ImageResponse[]>>(`/admin/products/${productId}/variants/${variantId}/images/reorder`, { order });
  return data.data;
};
