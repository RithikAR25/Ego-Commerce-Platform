/**
 * catalog.schemas.ts
 *
 * Zod validation schemas for catalog forms.
 * All schemas MUST mirror the exact backend validation annotations:
 *   - CreateAttributeTypeRequest.java
 *   - CreateAttributeValueRequest.java
 *   - CreateVariantRequest.java
 *   - UpdateVariantRequest.java
 */

import { z } from 'zod';

export const createCategorySchema = z.object({
  name:        z.string().min(2, 'Name must be at least 2 characters').max(100),
  code:        z.string().min(2, 'Code must be at least 2 characters').max(10).toUpperCase()
                .regex(/^[A-Z]+$/, 'Code must be uppercase letters only'),
  description: z.string().max(1000).optional(),
  /**
   * Zero or more root category IDs.
   * Empty array → root category.
   * One or more → subcategory with multiple parents (first is canonical).
   */
  parentIds:   z.array(z.number().positive()).max(10).optional(),
});

export type CreateCategoryFormValues = z.infer<typeof createCategorySchema>;

/**
 * Matches UpdateCategoryRequest.java
 * Code is NOT included — it is immutable after creation (embedded in variant SKUs).
 */
export const updateCategorySchema = z.object({
  name:         z.string().min(2, 'Name must be at least 2 characters').max(100),
  description:  z.string().max(1000).optional().or(z.literal('')),
  imageUrl:     z.string().max(500).url('Must be a valid URL').optional().or(z.literal('')),
  displayOrder: z.number().int().min(0).optional(),
  slug: z.string()
    .max(150)
    .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, "Slug must be lowercase, alphanumeric, hyphen-separated (e.g. 'oversized-tees')")
    .optional()
    .or(z.literal('')),
});

export type UpdateCategoryFormValues = z.infer<typeof updateCategorySchema>;

export const createProductSchema = z.object({
  name:             z.string().min(2, 'Name must be at least 2 characters').max(255),
  categoryId:       z.number().positive('Select a subcategory'),
  description:      z.string().max(5000).optional().or(z.literal('')),
  material:         z.string().max(255).optional().or(z.literal('')),
  careInstructions: z.string().max(2000).optional().or(z.literal('')),
  tags:             z.array(z.string().min(1).max(50)).max(20).optional(),
});

export type CreateProductFormValues = z.infer<typeof createProductSchema>;

/** Matches CreateAttributeTypeRequest.java validation rules */
export const createAttributeTypeSchema = z.object({
  name:         z.string().min(1, 'Attribute type name is required').max(50, 'Must not exceed 50 characters'),
  displayOrder: z.number().int().optional(),
});

export type CreateAttributeTypeFormValues = z.infer<typeof createAttributeTypeSchema>;

/** Matches CreateAttributeValueRequest.java validation rules */
export const createAttributeValueSchema = z.object({
  value:          z.string().min(1, 'Value label is required').max(100),
  code:           z.string().min(1, 'Code is required').max(10, 'Code must not exceed 10 characters')
                   .regex(/^[A-Z0-9]+$/, 'Code must be uppercase letters and digits only'),
  displayOrder:   z.number().int().optional(),
  hexColor:       z.string().regex(/^#[0-9A-Fa-f]{6}$/, 'Must be a valid hex color, e.g. #1A1A1A').optional().or(z.literal('')),
  swatchImageUrl: z.string().max(500).url('Must be a valid URL').optional().or(z.literal('')),
});

export type CreateAttributeValueFormValues = z.infer<typeof createAttributeValueSchema>;

/**
 * Matches CreateVariantRequest.java validation rules exactly.
 * Both colorAttributeValueId and sizeAttributeValueId are @NotNull — required.
 */
export const createVariantSchema = z.object({
  colorAttributeValueId: z.number().positive('Select a color'),
  sizeAttributeValueId:  z.number().positive('Select a size'),
  price:                 z.number().positive('Price must be greater than 0'),
  compareAtPrice:        z.number().positive().optional(),
  costPrice:             z.number().min(0).optional(),
  initialStock:          z.number().int().min(0, 'Stock cannot be negative'),
  lowStockThreshold:     z.number().int().min(1, 'Low stock threshold must be at least 1'),
  weightGrams:           z.number().int().positive().optional(),
}).refine(
  (d) => !d.compareAtPrice || d.compareAtPrice > d.price,
  { message: 'Compare-at price must be greater than price', path: ['compareAtPrice'] },
);

export type CreateVariantFormValues = z.infer<typeof createVariantSchema>;

export const updateVariantSchema = z.object({
  price:          z.number().positive('Price must be > 0'),
  compareAtPrice: z.number().positive().optional(),
  costPrice:      z.number().positive().optional(),
  weightGrams:    z.number().int().positive().optional(),
  active:         z.boolean().optional(),
}).refine(
  (d) => !d.compareAtPrice || d.compareAtPrice > d.price,
  { message: 'Compare-at price must be greater than price', path: ['compareAtPrice'] },
);

export type UpdateVariantFormValues = z.infer<typeof updateVariantSchema>;

/**
 * Matches UpdateInventoryRequest.java — field is quantityAvailable, not quantity.
 */
export const setInventorySchema = z.object({
  quantityAvailable: z.number().int().min(0, 'Quantity cannot be negative'),
});

export type SetInventoryFormValues = z.infer<typeof setInventorySchema>;
