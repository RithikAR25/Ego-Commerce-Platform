package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.CreateAttributeTypeRequest;
import com.ego.raw_ego.catalog.dto.request.CreateAttributeValueRequest;
import com.ego.raw_ego.catalog.dto.response.AttributeTypeDetailResponse;

import java.util.List;

/**
 * Contract for attribute types and values management for products.
 *
 * <p>The correct product lifecycle for variant creation:
 * <ol>
 *   <li>Create product (DRAFT)</li>
 *   <li>Add attribute types: "Color", "Size"</li>
 *   <li>Add attribute values: "Black"/"BLK", "M", "L"</li>
 *   <li>Create variants using colorAttributeValueId + sizeAttributeValueId</li>
 * </ol>
 */
public interface AttributeService {

    /**
     * Returns all attribute types (with their values) for a product.
     * Used by admin UI to populate the Add Variant dropdowns.
     */
    List<AttributeTypeDetailResponse> getAttributeTypes(Long productId);

    /**
     * Creates a new attribute type (e.g. "Color" or "Size") for a product.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if name already exists for this product
     */
    AttributeTypeDetailResponse createAttributeType(Long productId, CreateAttributeTypeRequest request);

    /**
     * Adds a value to an existing attribute type.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if code already exists in this type
     */
    AttributeTypeDetailResponse createAttributeValue(Long attributeTypeId, CreateAttributeValueRequest request);
}
