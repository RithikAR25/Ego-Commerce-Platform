package com.ego.raw_ego.catalog.controller;

import com.ego.raw_ego.catalog.dto.request.CreateAttributeTypeRequest;
import com.ego.raw_ego.catalog.dto.request.CreateAttributeValueRequest;
import com.ego.raw_ego.catalog.dto.response.AttributeTypeDetailResponse;
import com.ego.raw_ego.catalog.service.AttributeService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for managing product attribute types and values.
 *
 * <p>This is STEP 2 and STEP 3 of the product lifecycle before variants can be created:
 * <pre>
 *   STEP 1: POST /api/v1/admin/products          → create product (DRAFT)
 *   STEP 2: POST /api/v1/admin/products/{id}/attribute-types   → add "Color", "Size"
 *   STEP 3: POST /api/v1/admin/attribute-types/{typeId}/values → add "Black", "M", etc.
 *   STEP 4: POST /api/v1/admin/products/{id}/variants          → create variant
 * </pre>
 *
 * <p>All endpoints require ADMIN role.
 */
@RestController
@Tag(name = "Product Attributes", description = "Admin — manage attribute types and values for variant matrix")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AttributeController {

    private final AttributeService attributeService;

    /**
     * Returns all attribute types (with their values) for a product.
     * Used by the admin frontend to populate the Add Variant dropdowns.
     */
    @GetMapping("/api/v1/admin/products/{productId}/attribute-types")
    @Operation(
        summary = "[Admin] Get product attribute types",
        description = "Returns all attribute types (e.g. Color, Size) with their values for a product. " +
                      "Used by the frontend to populate variant creation dropdowns."
    )
    public ResponseEntity<ApiResponse<List<AttributeTypeDetailResponse>>> getAttributeTypes(
            @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(attributeService.getAttributeTypes(productId)));
    }

    /**
     * Creates a new attribute type for a product (e.g. "Color" or "Size").
     * Names are case-insensitive and must be unique per product.
     */
    @PostMapping("/api/v1/admin/products/{productId}/attribute-types")
    @Operation(
        summary = "[Admin] Create attribute type",
        description = "Creates a new attribute axis for a product (e.g. 'Color', 'Size'). " +
                      "Must be created before attribute values can be added."
    )
    public ResponseEntity<ApiResponse<AttributeTypeDetailResponse>> createAttributeType(
            @PathVariable Long productId,
            @Valid @RequestBody CreateAttributeTypeRequest request) {
        AttributeTypeDetailResponse response = attributeService.createAttributeType(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attribute type created successfully.", response));
    }

    /**
     * Adds a value to an existing attribute type.
     * Example: Add "Black" (code="BLK", hexColor="#1A1A1A") under "Color".
     */
    @PostMapping("/api/v1/admin/attribute-types/{attributeTypeId}/values")
    @Operation(
        summary = "[Admin] Add attribute value",
        description = "Adds a specific value to an attribute type. " +
                      "Example: add 'Black' (code=BLK) under the 'Color' type. " +
                      "The code becomes part of the immutable SKU — choose carefully."
    )
    public ResponseEntity<ApiResponse<AttributeTypeDetailResponse>> createAttributeValue(
            @PathVariable Long attributeTypeId,
            @Valid @RequestBody CreateAttributeValueRequest request) {
        AttributeTypeDetailResponse response = attributeService.createAttributeValue(attributeTypeId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attribute value added successfully.", response));
    }
}
