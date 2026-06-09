/**
 * catalog.types.ts
 *
 * TypeScript interfaces for the catalog domain.
 * These MUST mirror the exact backend DTO field names — never invent field names.
 *
 * Key backend DTOs this maps to:
 *   - ProductDetailResponse.java
 *   - VariantResponse.java → AttributeValueResponse inner class
 *   - AttributeTypeDetailResponse.java
 *   - CreateVariantRequest.java
 */

import type { ImageResponse } from './image.types';

export interface CategoryResponse {
  id:           number;
  name:         string;
  code:         string;
  slug:         string;
  description:  string | null;
  imageUrl:     string | null;
  displayOrder: number;
  active:       boolean;

  /**
   * Depth level of this category in the 3-level enterprise hierarchy.
   * "ROOT" (depth=0) | "GROUP" (depth=1) | "LEAF" (depth=2)
   */
  level: 'ROOT' | 'GROUP' | 'LEAF';

  /**
   * Number of products directly assigned to this category.
   * Meaningful only for LEAF categories.
   * Present only on the admin listing endpoint.
   */
  productCount?: number;

  /** Canonical primary parent — null for ROOT categories. */
  parent: CategoryResponse | null;
}

/**
 * A GROUP or LEAF entry within a parent's navigation tree section.
 * Carries link-level metadata (per-parent ordering, visibility, label override).
 */
export interface CategoryTreeItemResponse {
  id:            number;
  name:          string;
  code:          string;
  slug:          string;
  imageUrl:      string | null;
  displayOrder:  number;        // per-parent ordering from the hierarchy link
  visible:       boolean;       // visibility within this parent's nav section
  resolvedLabel: string;        // navigationLabel override or child's own name
  primary:       boolean;       // true = canonical parent link
  /**
   * LEAF categories under this GROUP item.
   * Populated when this item is at GROUP level (depth=1).
   * Always empty for LEAF items themselves.
   */
  leafCategories: CategoryTreeItemResponse[];
}

export interface CategoryTreeResponse {
  id:            number;
  name:          string;
  code:          string;
  slug:          string;
  imageUrl:      string | null;
  displayOrder:  number;
  /** GROUP categories under this ROOT, each carrying their leaf categories. */
  groups: CategoryTreeItemResponse[];
}

/** Matches CategoryHierarchyLinkResponse.java */
export interface CategoryHierarchyLinkResponse {
  id:              number;
  parentId:        number;
  parentName:      string;
  parentSlug:      string;
  childId:         number;
  childName:       string;
  childSlug:       string;
  primary:         boolean;
  displayOrder:    number;
  visible:         boolean;
  navigationLabel: string | null;
  resolvedLabel:   string;
  createdAt:       string;
  updatedAt:       string;
}

/**
 * Matches VariantResponse.AttributeValueResponse (inner class).
 * Used inside ProductVariantResponse.attributeValues[].
 * Note: hexColor (not colorCode) — field name matches DB column hex_color.
 */
export interface AttributeValueResponse {
  id:             number;
  typeId:         number;
  typeName:       string;
  value:          string;
  code:           string;
  hexColor:       string | null;
  swatchImageUrl: string | null;
}

/**
 * Matches ProductDetailResponse.AttributeTypeResponse (inner class).
 * Used in product.attributeTypes[] — drives the variant selector dropdowns.
 */
export interface AttributeTypeResponse {
  id:           number;
  name:         string;
  displayOrder: number;
  values:       AttributeValueSummary[];
}

/**
 * Matches ProductDetailResponse.AttributeValueSummary (inner class).
 * Simpler than the per-variant AttributeValueResponse — used in the attribute matrix.
 */
export interface AttributeValueSummary {
  id:             number;
  value:          string;
  code:           string;
  hexColor:       string | null;
  swatchImageUrl: string | null;
  displayOrder:   number;
}

/**
 * Matches AttributeTypeDetailResponse.java — returned by the attribute management API.
 * Used in the admin attribute management UI panel.
 */
export interface AttributeTypeDetailResponse {
  id:           number;
  name:         string;
  displayOrder: number;
  values:       AttributeValueDetail[];
}

export interface AttributeValueDetail {
  id:             number;
  value:          string;
  code:           string;
  displayOrder:   number;
  hexColor:       string | null;
  swatchImageUrl: string | null;
}

export type InventoryStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';

export interface InventoryResponse {
  quantityAvailable: number;
  lowStockThreshold: number;
  status:            InventoryStatus;
}

/**
 * Matches InventoryItemResponse.java — the admin-only rich inventory view.
 * Returned by GET /api/v1/admin/inventory.
 */
export interface InventoryItemResponse {
  variantId:         number;
  sku:               string;
  productId:         number;
  productName:       string;
  productSlug:       string;
  variantLabel:      string;   // e.g. "Black / M"
  quantityAvailable: number;
  quantityReserved:  number;
  lowStockThreshold: number;
  stockStatus:       InventoryStatus;
  updatedAt:         string;
}

/** Query parameters for GET /api/v1/admin/inventory */
export interface InventoryFilterParams {
  status?:  InventoryStatus | '';
  search?:  string;
  page?:    number;
  size?:    number;
  sortBy?:  'sku' | 'quantityAvailable' | 'quantityReserved' | 'stockStatus' | 'productName';
  sortDir?: 'asc' | 'desc';
}

/** Matches VariantResponse.java */
export interface ProductVariantResponse {
  id:                  number;
  sku:                 string;
  price:               number;
  compareAtPrice:      number | null;
  discountPercent:     number | null;
  weightGrams:         number | null;
  active:              boolean;
  stockStatus:         InventoryStatus;
  quantityAvailable?:  number;
  lowStock?:           boolean;
  stockUrgencyMessage?: string | null;
  attributeValues:     AttributeValueResponse[];
  images:              ImageResponse[];
}

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'OUT_OF_STOCK' | 'ARCHIVED';

export interface ProductSummaryResponse {
  id:              number;
  name:            string;
  slug:            string;
  status:          ProductStatus;
  primaryImageUrl: string | null;
  minPrice:        number | null;
  maxPrice:        number | null;
  categoryName:    string;
  createdAt:       string;
}

/** Matches ProductDetailResponse.java */
export interface ProductDetailResponse {
  id:               number;
  name:             string;
  slug:             string;
  productCode:      string;
  status:           ProductStatus;
  description:      string | null;
  material:         string | null;
  careInstructions: string | null;
  tags:             string[];
  category:         CategoryResponse;
  attributeTypes:   AttributeTypeResponse[];
  variants:         ProductVariantResponse[];
  galleryImages:    ImageResponse[];
  minPrice:         number | null;
  maxPrice:         number | null;
  createdAt:        string;
  updatedAt:        string;
}

export interface SpringPage<T> {
  content:          T[];
  totalElements:    number;
  totalPages:       number;
  number:           number;
  size:             number;
  first:            boolean;
  last:             boolean;
  numberOfElements: number;
  empty:            boolean;
}

// ── Request Payload Types ──────────────────────────────────────────────────────

export interface CreateProductPayload {
  name:              string;
  categoryId:        number;
  description?:      string;
  material?:         string;
  careInstructions?: string;
  tags?:             string[];
}

/** Matches CreateAttributeTypeRequest.java */
export interface CreateAttributeTypePayload {
  name:          string;
  displayOrder?: number;
}

/** Matches CreateAttributeValueRequest.java */
export interface CreateAttributeValuePayload {
  value:           string;
  code:            string;
  displayOrder?:   number;
  hexColor?:       string;
  swatchImageUrl?: string;
}

/** Matches CreateVariantRequest.java exactly */
export interface CreateVariantPayload {
  colorAttributeValueId: number;
  sizeAttributeValueId:  number;
  price:                 number;
  compareAtPrice?:       number;
  costPrice?:            number;
  initialStock:          number;
  lowStockThreshold:     number;
  weightGrams?:          number;
}

export interface UpdateVariantPayload {
  price:           number;
  compareAtPrice?: number;
  costPrice?:      number;
  weightGrams?:    number;
  active?:         boolean;
}

export interface UpdateProductStatusPayload {
  status: ProductStatus;
}

/**
 * Matches UpdateInventoryRequest.java — field is quantityAvailable (NOT quantity).
 */
export interface SetInventoryPayload {
  quantityAvailable:  number;
  lowStockThreshold?: number;
}

export interface CreateCategoryPayload {
  name:          string;
  code:          string;
  description?:  string;
  imageUrl?:     string;
  displayOrder?: number;
  /**
   * Parent root category IDs.
   * - Empty / omitted → root category
   * - One entry       → subcategory with a single parent
   * - Multiple        → unisex / cross-listed subcategory; first is canonical
   */
  parentIds?:    number[];
}

/** Matches AddHierarchyLinkRequest.java */
export interface AddHierarchyLinkPayload {
  parentId:         number;
  displayOrder?:    number;
  visible?:         boolean;
  navigationLabel?: string;
}

/** Matches UpdateCategoryRequest.java */
export interface UpdateCategoryPayload {
  /** Required — category name. Slug auto-regenerates if name changes. */
  name:           string;
  /** Optional — clears description if empty string. */
  description?:   string;
  /** Optional — clears imageUrl if empty string. */
  imageUrl?:      string;
  displayOrder?:  number;
  /**
   * Optional explicit slug override.
   * If omitted, slug is auto-generated from the new name.
   * Must be lowercase, alphanumeric, hyphen-separated.
   * WARNING: changing slug breaks existing bookmarked/cached URLs.
   */
  slug?:          string;
}

/** Matches UpdateHierarchyLinkRequest.java */
export interface UpdateHierarchyLinkPayload {
  displayOrder?:    number;
  visible?:         boolean;
  navigationLabel?: string | null;
}
