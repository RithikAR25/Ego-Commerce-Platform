/**
 * useAdminCatalog.ts
 *
 * Admin TanStack Query hooks and mutations for the catalog domain.
 * (Force re-parse)
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminKeys, productKeys, categoryKeys, imageKeys } from './queryKeys';
import {
  adminGetAllCategories,
  adminCreateCategory,
  adminUpdateCategory,
  adminDeactivateCategory,
  adminActivateCategory,
  adminHardDeleteCategory,
  adminGetParentLinks,
  adminAddParentToCategory,
  adminRemoveParentFromCategory,
  adminSetPrimaryParent,
  adminGetProducts,
  adminGetProductBySlug,
  adminCreateProduct,
  adminUpdateProductStatus,
  adminArchiveProduct,
  adminHardDeleteProduct,
  adminCreateVariant,
  adminUpdateVariant,
  adminSetInventory,
  adminGetInventory,
  adminUpdateThreshold,
  adminGetAttributeTypes,
  adminCreateAttributeType,
  adminCreateAttributeValue,
  adminUploadGalleryImage,
  adminDeleteGalleryImage,
  adminReorderGalleryImages,
  adminUploadVariantImage,
  adminDeleteVariantImage,
  adminSetPrimaryVariantImage,
  adminReorderVariantImages,
} from '@/api/admin.catalog.api';
import type {
  CreateProductPayload,
  CreateVariantPayload,
  UpdateVariantPayload,
  UpdateProductStatusPayload,
  SetInventoryPayload,
  CreateCategoryPayload,
  UpdateCategoryPayload,
  AddHierarchyLinkPayload,
  CreateAttributeTypePayload,
  CreateAttributeValuePayload,
  InventoryFilterParams,
} from '@/types/catalog.types';
import type { ReorderItem } from '@/types/image.types';

export const useAdminCategories = () =>
  useQuery({
    queryKey: adminKeys.categories(),
    queryFn: adminGetAllCategories,
    staleTime: 5 * 60 * 1000,
  });

export const useCreateCategory = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCategoryPayload) => adminCreateCategory(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.categories() });
      qc.invalidateQueries({ queryKey: categoryKeys.tree() });
    },
  });
};

export const useDeactivateCategory = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => adminDeactivateCategory(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.categories() });
      qc.invalidateQueries({ queryKey: categoryKeys.tree() });
    },
  });
};

/**
 * Re-activates a soft-deactivated category, restoring its storefront visibility.
 * Invalidates both the admin list and the public navigation tree cache.
 */
export const useActivateCategory = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => adminActivateCategory(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.categories() });
      qc.invalidateQueries({ queryKey: categoryKeys.tree() });
    },
  });
};

/**
 * Permanently (hard) deletes a category.
 * The backend requires the category to be already inactive,
 * have no products, and (if root) have no subcategories.
 * On success the category row disappears from the list.
 */
export const useHardDeleteCategory = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => adminHardDeleteCategory(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.categories() });
      qc.invalidateQueries({ queryKey: categoryKeys.tree() });
    },
  });
};

export const useUpdateCategory = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: UpdateCategoryPayload }) =>
      adminUpdateCategory(id, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.categories() });
      qc.invalidateQueries({ queryKey: categoryKeys.tree() });
    },
  });
};

export const useAdminParentLinks = (childId: number) =>
  useQuery({
    queryKey: [...adminKeys.categories(), 'parentLinks', childId],
    queryFn:  () => adminGetParentLinks(childId),
    enabled:  childId > 0,
    staleTime: 30 * 1000,
  });

export const useAddParentToCategory = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ childId, payload }: { childId: number; payload: AddHierarchyLinkPayload }) =>
      adminAddParentToCategory(childId, payload),
    onSuccess: (_data, { childId }) => {
      qc.invalidateQueries({ queryKey: [...adminKeys.categories(), 'parentLinks', childId] });
      qc.invalidateQueries({ queryKey: adminKeys.categories() });
      qc.invalidateQueries({ queryKey: categoryKeys.tree() });
    },
  });
};

export const useRemoveParentFromCategory = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ childId, parentId }: { childId: number; parentId: number }) =>
      adminRemoveParentFromCategory(childId, parentId),
    onSuccess: (_data, { childId }) => {
      qc.invalidateQueries({ queryKey: [...adminKeys.categories(), 'parentLinks', childId] });
      qc.invalidateQueries({ queryKey: adminKeys.categories() });
      qc.invalidateQueries({ queryKey: categoryKeys.tree() });
    },
  });
};

export const useSetPrimaryParent = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ childId, parentId }: { childId: number; parentId: number }) =>
      adminSetPrimaryParent(childId, parentId),
    onSuccess: (_data, { childId }) => {
      qc.invalidateQueries({ queryKey: [...adminKeys.categories(), 'parentLinks', childId] });
      qc.invalidateQueries({ queryKey: adminKeys.categories() });
      qc.invalidateQueries({ queryKey: categoryKeys.tree() });
    },
  });
};

export const useAdminProducts = (page = 0, size = 20) =>
  useQuery({
    queryKey: adminKeys.products({ page, size }),
    queryFn: () => adminGetProducts(page, size),
    staleTime: 30 * 1000,
  });

export const useAdminProductDetail = (slug: string) =>
  useQuery({
    queryKey: productKeys.detail(slug),
    queryFn: () => adminGetProductBySlug(slug),
    enabled: Boolean(slug),
    staleTime: 30 * 1000,
  });

export const useCreateProduct = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateProductPayload) => adminCreateProduct(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.products() });
    },
  });
};

export const useUpdateProductStatus = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, payload }: { productId: number; payload: UpdateProductStatusPayload }) =>
      adminUpdateProductStatus(productId, payload),
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: adminKeys.products() });
      qc.invalidateQueries({ queryKey: productKeys.detail(updated.slug) });
    },
  });
};

export const useArchiveProduct = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (productId: number) => adminArchiveProduct(productId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.products() });
      qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

/**
 * Permanently (hard) deletes an archived product.
 * The backend enforces: ARCHIVED status + no order history.
 * On success the product row is gone — navigates back to /admin/products.
 */
export const useHardDeleteProduct = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (productId: number) => adminHardDeleteProduct(productId),
    onSuccess: () => {
      // Remove all product queries — the product no longer exists
      qc.removeQueries({ queryKey: adminKeys.products() });
      qc.removeQueries({ queryKey: productKeys.all() });
    },
  });
};

// ── Attribute Type & Value Hooks ─────────────────────────────────────────────

/**
 * Fetches all attribute types (Color, Size) with their values for a product.
 * Used to populate the Add Variant dialog dropdowns.
 */
export const useAttributeTypes = (productId: number) =>
  useQuery({
    queryKey: adminKeys.attributeTypes(productId),
    queryFn: () => adminGetAttributeTypes(productId),
    enabled: productId > 0,
    staleTime: 60 * 1000,
  });

/** Creates a new attribute type (e.g. "Color", "Size") for a product. */
export const useCreateAttributeType = (productId: number, productSlug: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateAttributeTypePayload) =>
      adminCreateAttributeType(productId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.attributeTypes(productId) });
      qc.invalidateQueries({ queryKey: productKeys.detail(productSlug) });
    },
  });
};

/** Adds a value (e.g. "Black"/"BLK") to an existing attribute type. */
export const useCreateAttributeValue = (productId: number, productSlug: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ attributeTypeId, payload }: { attributeTypeId: number; payload: CreateAttributeValuePayload }) =>
      adminCreateAttributeValue(attributeTypeId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.attributeTypes(productId) });
      qc.invalidateQueries({ queryKey: productKeys.detail(productSlug) });
    },
  });
};

// ── Variant Hooks ─────────────────────────────────────────────────────────────

export const useCreateVariant = (productSlug: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, payload }: { productId: number; payload: CreateVariantPayload }) =>
      adminCreateVariant(productId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: productKeys.detail(productSlug) });
      qc.invalidateQueries({ queryKey: adminKeys.products() });
    },
  });
};

export const useUpdateVariant = (productSlug: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ variantId, payload }: { variantId: number; payload: UpdateVariantPayload }) =>
      adminUpdateVariant(variantId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: productKeys.detail(productSlug) });
    },
  });
};

export const useUploadGalleryImage = (productId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ file, altText, displayOrder }: { file: File; altText?: string; displayOrder?: number }) =>
      adminUploadGalleryImage(productId, file, altText, displayOrder),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: imageKeys.gallery(productId) });
      qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

export const useDeleteGalleryImage = (productId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (imageId: number) => adminDeleteGalleryImage(productId, imageId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: imageKeys.gallery(productId) });
      qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

export const useReorderGalleryImages = (productId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (order: ReorderItem[]) => adminReorderGalleryImages(productId, order),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: imageKeys.gallery(productId) });
      qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

export const useUploadVariantImage = (productId: number, variantId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ file, altText, displayOrder }: { file: File; altText?: string; displayOrder?: number }) =>
      adminUploadVariantImage(productId, variantId, file, altText, displayOrder),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: imageKeys.variant(productId, variantId) });
      qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

export const useDeleteVariantImage = (productId: number, variantId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (imageId: number) => adminDeleteVariantImage(productId, variantId, imageId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: imageKeys.variant(productId, variantId) });
      qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

export const useSetPrimaryVariantImage = (productId: number, variantId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (imageId: number) => adminSetPrimaryVariantImage(productId, variantId, imageId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: imageKeys.variant(productId, variantId) });
      qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

export const useReorderVariantImages = (productId: number, variantId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (order: ReorderItem[]) => adminReorderVariantImages(productId, variantId, order),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: imageKeys.variant(productId, variantId) });
      qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

// ── Inventory hooks ───────────────────────────────────────────────────────────

const inventoryKeys = {
  all:    () => ['admin', 'inventory'] as const,
  list:   (params: InventoryFilterParams) => ['admin', 'inventory', 'list', params] as const,
};

/**
 * Paginated inventory list — filterable by status / search.
 * Stale after 30s so hot-reloading the page gives fresh data without hammering the server.
 */
export const useInventory = (params: InventoryFilterParams = {}) =>
  useQuery({
    queryKey:  inventoryKeys.list(params),
    queryFn:   () => adminGetInventory(params),
    staleTime: 30_000,
    placeholderData: (prev) => prev,
  });

/**
 * Mutation: update inventory quantity (and optionally threshold).
 * Invalidates the whole inventory list on success so the table refreshes.
 */
export const useSetInventory = (productSlug?: string) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ variantId, payload }: { variantId: number; payload: SetInventoryPayload }) =>
      adminSetInventory(variantId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: inventoryKeys.all() });
      if (productSlug) qc.invalidateQueries({ queryKey: productKeys.all() });
    },
  });
};

/**
 * Mutation: update ONLY the low-stock threshold.
 * Invalidates the whole inventory list so statuses recalculate immediately.
 */
export const useUpdateThreshold = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ variantId, threshold }: { variantId: number; threshold: number }) =>
      adminUpdateThreshold(variantId, threshold),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: inventoryKeys.all() });
    },
  });
};
