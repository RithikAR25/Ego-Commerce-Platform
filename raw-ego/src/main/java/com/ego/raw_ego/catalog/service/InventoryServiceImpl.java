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
 * Implementation of {@link InventoryService} — admin inventory management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRecordRepository inventoryRepository;
    private final ProductService            productService;
    private final SearchOutboxRepository    searchOutboxRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> getInventoryPage(
            String status, String search,
            int page, int size,
            String sortBy, String sortDir) {

        String statusFilter = StringUtils.hasText(status) ? status.toUpperCase() : null;
        String searchFilter = StringUtils.hasText(search) ? search.trim() : null;

        String jpqlSort = switch (sortBy == null ? "" : sortBy) {
            case "quantityAvailable" -> "ir.quantityAvailable";
            case "quantityReserved"  -> "ir.quantityReserved";
            case "stockStatus"       -> "ir.quantityAvailable";
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

    @Override
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

    @Override
    @Transactional
    public InventoryItemResponse updateThreshold(Long variantId, UpdateThresholdRequest request) {
        InventoryRecord ir = findByVariantId(variantId);
        ir.setLowStockThreshold(request.getLowStockThreshold());
        inventoryRepository.save(ir);

        log.info("Threshold updated: variantId={} threshold={}", variantId, request.getLowStockThreshold());
        return InventoryItemResponse.from(ir);
    }

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
