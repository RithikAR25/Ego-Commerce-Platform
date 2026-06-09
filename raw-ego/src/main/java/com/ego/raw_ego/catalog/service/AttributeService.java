package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.CreateAttributeTypeRequest;
import com.ego.raw_ego.catalog.dto.request.CreateAttributeValueRequest;
import com.ego.raw_ego.catalog.dto.response.AttributeTypeDetailResponse;
import com.ego.raw_ego.catalog.entity.AttributeType;
import com.ego.raw_ego.catalog.entity.AttributeValue;
import com.ego.raw_ego.catalog.entity.Product;
import com.ego.raw_ego.catalog.repository.AttributeTypeRepository;
import com.ego.raw_ego.catalog.repository.AttributeValueRepository;
import com.ego.raw_ego.catalog.repository.ProductRepository;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages attribute types and values for a product.
 *
 * <p>The correct product lifecycle for variant creation:
 * <ol>
 *   <li>Create product (DRAFT)</li>
 *   <li>Add attribute types: "Color", "Size"</li>
 *   <li>Add attribute values: "Black"/"BLK", "M", "L"</li>
 *   <li>Create variants using colorAttributeValueId + sizeAttributeValueId</li>
 * </ol>
 *
 * <p>Attribute types must be unique per product (case-insensitive name check).
 * Attribute value codes are used in SKU generation and must be uppercase-only.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AttributeService {

    private final ProductRepository       productRepository;
    private final AttributeTypeRepository attributeTypeRepository;
    private final AttributeValueRepository attributeValueRepository;

    /**
     * Returns all attribute types (with their values) for a product.
     * Used by admin UI to populate the Add Variant dropdowns.
     */
    @Transactional(readOnly = true)
    public List<AttributeTypeDetailResponse> getAttributeTypes(Long productId) {
        ensureProductExists(productId);
        return attributeTypeRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(AttributeTypeDetailResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Creates a new attribute type (e.g. "Color" or "Size") for a product.
     *
     * <p>Validation:
     * <ul>
     *   <li>Product must exist</li>
     *   <li>Name must be unique within the product (case-insensitive)</li>
     * </ul>
     */
    @Transactional
    public AttributeTypeDetailResponse createAttributeType(Long productId, CreateAttributeTypeRequest request) {
        Product product = ensureProductExists(productId);

        // Duplicate name guard
        attributeTypeRepository.findByProductIdAndNameIgnoreCase(productId, request.getName())
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Attribute type '" + request.getName() + "' already exists for this product.");
                });

        AttributeType type = AttributeType.builder()
                .product(product)
                .name(request.getName().trim())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        type = attributeTypeRepository.save(type);
        log.info("AttributeType created: id={} name='{}' productId={}", type.getId(), type.getName(), productId);
        return AttributeTypeDetailResponse.from(type);
    }

    /**
     * Adds a value to an existing attribute type.
     *
     * <p>Example: Add "Black" (code="BLK", hexColor="#1A1A1A") under "Color".
     *
     * <p>Validation:
     * <ul>
     *   <li>Attribute type must exist</li>
     *   <li>Code must be unique within the attribute type</li>
     * </ul>
     */
    @Transactional
    public AttributeTypeDetailResponse createAttributeValue(Long attributeTypeId, CreateAttributeValueRequest request) {
        AttributeType type = attributeTypeRepository.findById(attributeTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attribute type not found: id=" + attributeTypeId));

        // Code uniqueness guard within this type
        boolean codeExists = type.getValues().stream()
                .anyMatch(v -> v.getCode().equalsIgnoreCase(request.getCode()));
        if (codeExists) {
            throw new ConflictException(
                    "Attribute value with code '" + request.getCode() + "' already exists in this type.");
        }

        AttributeValue value = AttributeValue.builder()
                .attributeType(type)
                .value(request.getValue().trim())
                .code(request.getCode().toUpperCase())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .hexColor(request.getHexColor())
                .swatchImageUrl(request.getSwatchImageUrl())
                .build();

        attributeValueRepository.save(value);
        log.info("AttributeValue created: code='{}' typeId={} productId={}",
                value.getCode(), attributeTypeId, type.getProduct().getId());

        // Reload the type to pick up the new value in the response
        type = attributeTypeRepository.findById(attributeTypeId).orElseThrow();
        return AttributeTypeDetailResponse.from(type);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Product ensureProductExists(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: id=" + productId));
    }
}
