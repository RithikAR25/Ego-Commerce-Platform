package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.UpdateInventoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateThresholdRequest;
import com.ego.raw_ego.catalog.dto.response.InventoryItemResponse;
import org.springframework.data.domain.Page;

/**
 * Contract for admin inventory management operations.
 *
 * <p>Reservation lifecycle (reserve / commit / release / restore) is in
 * {@link com.ego.raw_ego.cart.service.InventoryReservationService} —
 * those are cart-driven operations, not admin operations.
 */
public interface InventoryService {

    /**
     * Returns a paginated view of all product variant inventory records.
     *
     * @param status  optional status filter: IN_STOCK | LOW_STOCK | OUT_OF_STOCK
     * @param search  optional SKU / product-name substring (case-insensitive)
     * @param page    0-based page index
     * @param size    page size (capped at 200 in controller)
     * @param sortBy  field to sort by: "sku" | "quantityAvailable" | "stockStatus"
     * @param sortDir "asc" | "desc"
     */
    Page<InventoryItemResponse> getInventoryPage(
            String status, String search,
            int page, int size,
            String sortBy, String sortDir);

    /**
     * Sets the absolute {@code quantityAvailable} for a variant.
     * Optionally updates {@code lowStockThreshold}. Syncs product status and ES outbox.
     */
    InventoryItemResponse updateInventory(Long variantId, UpdateInventoryRequest request);

    /**
     * Updates only the low-stock alert threshold — does not touch quantity.
     */
    InventoryItemResponse updateThreshold(Long variantId, UpdateThresholdRequest request);
}
