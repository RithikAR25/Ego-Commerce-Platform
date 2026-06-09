package com.ego.raw_ego.catalog.controller;

import com.ego.raw_ego.catalog.dto.request.CreateVariantRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateVariantRequest;
import com.ego.raw_ego.catalog.dto.response.VariantResponse;
import com.ego.raw_ego.catalog.service.ProductVariantService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Variant management — Admin only.
 *
 * <pre>
 *   POST   /api/v1/admin/products/{id}/variants  → create variant (auto-generates SKU)
 *   PUT    /api/v1/admin/variants/{id}            → update pricing / active status
 * </pre>
 *
 * <p>Inventory endpoints (GET list, PUT quantity, PATCH threshold) have been moved to
 * {@link InventoryController} to maintain single-responsibility.
 */
@RestController
@Tag(name = "Variants & Inventory", description = "Admin-only variant management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService variantService;

    @PostMapping("/api/v1/admin/products/{productId}/variants")
    @Operation(summary = "[Admin] Create product variant",
               description = "Creates a new variant and its inventory record. " +
                             "SKU is auto-generated: EGO-{CAT}-{PROD}-{COLOR}-{SIZE}. " +
                             "colorAttributeValueId and sizeAttributeValueId must belong to this product.")
    public ResponseEntity<ApiResponse<VariantResponse>> createVariant(
            @PathVariable Long productId,
            @Valid @RequestBody CreateVariantRequest request) {
        VariantResponse response = variantService.createVariant(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Variant created successfully.", response));
    }

    @PutMapping("/api/v1/admin/variants/{variantId}")
    @Operation(summary = "[Admin] Update variant",
               description = "Updates pricing (price, compareAtPrice, costPrice) and/or active status. " +
                             "SKU is immutable — cannot be changed.")
    public ResponseEntity<ApiResponse<VariantResponse>> updateVariant(
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateVariantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Variant updated.", variantService.updateVariant(variantId, request)));
    }
}
