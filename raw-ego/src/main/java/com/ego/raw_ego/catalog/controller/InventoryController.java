package com.ego.raw_ego.catalog.controller;

import com.ego.raw_ego.catalog.dto.request.UpdateInventoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateThresholdRequest;
import com.ego.raw_ego.catalog.dto.response.InventoryItemResponse;
import com.ego.raw_ego.catalog.service.InventoryService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only inventory management endpoints.
 *
 * <pre>
 *   GET    /api/v1/admin/inventory                         → paginated list (filterable)
 *   PUT    /api/v1/admin/inventory/{variantId}             → set quantity (+ optional threshold)
 *   PATCH  /api/v1/admin/inventory/{variantId}/threshold   → update threshold only
 * </pre>
 *
 * <p>Cart-driven reservation operations (reserve / commit / release / restore) are handled
 * internally by {@link com.ego.raw_ego.cart.service.InventoryReservationService}
 * and are NOT exposed as REST endpoints.
 */
@RestController
@Tag(name = "Inventory", description = "Admin inventory management — stock levels and alert thresholds")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // ── Read ─────────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/admin/inventory")
    @Operation(
        summary     = "[Admin] List inventory",
        description = """
            Returns a paginated list of all product variant inventory records.

            **Filtering:**
            - `status` — one of `IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK` (omit for all).
            - `search` — case-insensitive substring match on SKU or product name.

            **Sorting:** `sortBy` supports `sku`, `quantityAvailable`, `quantityReserved`, `stockStatus`, `productName`.
            Default: `sku ASC`.

            **Pagination:** `page` is 0-based. `size` is capped at 200.
            """
    )
    public ResponseEntity<ApiResponse<Page<InventoryItemResponse>>> getInventory(
            @Parameter(description = "Filter by stock status: IN_STOCK | LOW_STOCK | OUT_OF_STOCK")
            @RequestParam(required = false) String status,

            @Parameter(description = "Search by SKU or product name (case-insensitive)")
            @RequestParam(required = false) String search,

            @RequestParam(defaultValue = "0")   int    page,
            @RequestParam(defaultValue = "25")  int    size,
            @RequestParam(defaultValue = "sku") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Page<InventoryItemResponse> result =
                inventoryService.getInventoryPage(status, search, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Write ────────────────────────────────────────────────────────────────

    @PutMapping("/api/v1/admin/inventory/{variantId}")
    @Operation(
        summary     = "[Admin] Set inventory quantity",
        description = """
            Sets the absolute `quantityAvailable` for a variant.
            Optionally updates `lowStockThreshold` in the same call.
            Automatically syncs the parent product status (ACTIVE ↔ OUT_OF_STOCK).
            Publishes an Elasticsearch outbox event so the search index stays current.
            """
    )
    public ResponseEntity<ApiResponse<InventoryItemResponse>> updateInventory(
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateInventoryRequest request) {

        InventoryItemResponse updated = inventoryService.updateInventory(variantId, request);
        return ResponseEntity.ok(ApiResponse.success("Inventory updated.", updated));
    }

    @PatchMapping("/api/v1/admin/inventory/{variantId}/threshold")
    @Operation(
        summary     = "[Admin] Update low-stock alert threshold",
        description = """
            Updates **only** the `lowStockThreshold` for a variant — does not touch `quantityAvailable`.
            When `quantityAvailable ≤ lowStockThreshold` (and > 0) the variant shows a \"Low Stock\" badge
            on the storefront and counts towards the admin dashboard Low Stock Alerts KPI.
            """
    )
    public ResponseEntity<ApiResponse<InventoryItemResponse>> updateThreshold(
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateThresholdRequest request) {

        InventoryItemResponse updated = inventoryService.updateThreshold(variantId, request);
        return ResponseEntity.ok(ApiResponse.success("Threshold updated.", updated));
    }
}
