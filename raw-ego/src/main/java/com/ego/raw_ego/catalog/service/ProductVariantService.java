package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.CreateVariantRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateInventoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateVariantRequest;
import com.ego.raw_ego.catalog.dto.response.VariantResponse;

/**
 * Contract for product variant management.
 *
 * <p><b>SKU generation (LOCKED):</b>
 * {@code EGO-{SUBCATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE_CODE}}
 * The SKU is set once and NEVER updated.
 *
 * <p>An {@link com.ego.raw_ego.catalog.entity.InventoryRecord} is created
 * atomically with each variant.
 */
public interface ProductVariantService {

    /**
     * Creates a new variant and its inventory record atomically.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if the generated SKU already exists
     */
    VariantResponse createVariant(Long productId, CreateVariantRequest request);

    /**
     * Updates variant pricing and/or active status.
     * SKU is NEVER updated — immutable contract.
     */
    VariantResponse updateVariant(Long variantId, UpdateVariantRequest request);

    /**
     * Sets the absolute inventory quantity for a variant (admin stock adjustment).
     * Syncs product status afterwards.
     */
    void updateInventory(Long variantId, UpdateInventoryRequest request);
}
