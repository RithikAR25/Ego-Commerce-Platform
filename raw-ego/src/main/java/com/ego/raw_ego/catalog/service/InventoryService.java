package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.UpdateInventoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateThresholdRequest;
import com.ego.raw_ego.catalog.dto.response.InventoryItemResponse;
import com.ego.raw_ego.catalog.entity.InventoryRecord;
import com.ego.raw_ego.catalog.repository.InventoryRecordRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.search.entity.SearchOutboxEntry;
import com.ego.raw_ego.search.repository.SearchOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Inventory management service — admin operations only.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Paginated reads for the admin inventory dashboard (filterable by status / search).</li>
 *   <li>Atomic quantity and threshold updates with Elasticsearch outbox publication.</li>
 * </ul>
 *
 * <p>Reservation lifecycle (reserve / commit / release / restore) remains in
 * {@link com.ego.raw_ego.cart.service.InventoryReservationService} —
 * those are cart-driven operations, not admin operations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRecordRepository inventoryRepository;
    private final ProductService            productService;
    private final SearchOutboxRepository    searchOutboxRepository;

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated view of all product variant inventory records.
     *
     * @param status  optional status filter: IN_STOCK | LOW_STOCK | OUT_OF_STOCK
     * @param search  optional SKU / product-name substring (case-insensitive)
     * @param page    0-based page index
     * @param size    page size (capped at 200 in controller)
     * @param sortBy  field to sort by: "sku" | "quantityAvailable" | "stockStatus" (default: sku)
     * @param sortDir "asc" | "desc" (default: asc)
     */
    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> getInventoryPage(
            String status, String search,
            int page, int size,
            String sortBy, String sortDir) {

        String statusFilter = StringUtils.hasText(status) ? status.toUpperCase() : null;
        String searchFilter = StringUtils.hasText(search) ? search.trim() : null;

        // Resolve sortable column to JPQL path
        String jpqlSort = switch (sortBy == null ? "" : sortBy) {
            case "quantityAvailable" -> "ir.quantityAvailable";
            case "quantityReserved"  -> "ir.quantityReserved";
            case "stockStatus"       -> "ir.quantityAvailable";   // proxy: low qty → low status
            case "productName"       -> "v.product.name";
            default                  -> "v.sku";
        };

        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(Sort.Direction.DESC, jpqlSort)
                : Sort.by(Sort.Direction.ASC,  jpqlSort);

        PageRequest pageable = PageRequest.of(page, Math.min(size, 200), sort);

        return inventoryRepository
                .findAllWithFilters(statusFilter, searchFilter, pageable)
                .map(InventoryItemResponse::from);
    }

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Sets the absolute {@code quantityAvailable} for a variant.
     * Optionally updates {@code lowStockThreshold} in the same call.
     * Syncs product status and publishes an ES outbox event.
     */
    @Transactional
    public InventoryItemResponse updateInventory(Long variantId, UpdateInventoryRequest request) {
        InventoryRecord ir = findByVariantId(variantId);

        ir.setQuantityAvailable(request.getQuantityAvailable());
        if (request.getLowStockThreshold() != null) {
            ir.setLowStockThreshold(request.getLowStockThreshold());
        }

        inventoryRepository.save(ir);
        long productId = ir.getVariant().getProduct().getId();
        productService.syncProductStatusFromInventory(productId);
        publishOutbox(productId);

        log.info("Inventory updated: variantId={} qty={}", variantId, request.getQuantityAvailable());
        return InventoryItemResponse.from(inventoryRepository.findByVariantId(variantId).orElseThrow());
    }

    /**
     * Updates <em>only</em> the low-stock alert threshold — does not touch quantity.
     * Useful for ops teams that want to tune alert sensitivity without touching stock.
     */
    @Transactional
    public InventoryItemResponse updateThreshold(Long variantId, UpdateThresholdRequest request) {
        InventoryRecord ir = findByVariantId(variantId);
        ir.setLowStockThreshold(request.getLowStockThreshold());
        inventoryRepository.save(ir);

        log.info("Threshold updated: variantId={} threshold={}", variantId, request.getLowStockThreshold());
        return InventoryItemResponse.from(ir);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private InventoryRecord findByVariantId(Long variantId) {
        return inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory record not found for variant: id=" + variantId));
    }

    private void publishOutbox(Long productId) {
        searchOutboxRepository.save(
            SearchOutboxEntry.builder()
                .productId(productId)
                .eventType(SearchOutboxEntry.EventType.UPSERT)
                .status(SearchOutboxEntry.Status.PENDING)
                .build()
        );
    }
}
