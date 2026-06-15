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
 * Implementation of {@link AttributeService} — manages attribute types and values for products.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AttributeServiceImpl implements AttributeService {

    private final ProductRepository       productRepository;
    private final AttributeTypeRepository attributeTypeRepository;
    private final AttributeValueRepository attributeValueRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AttributeTypeDetailResponse> getAttributeTypes(Long productId) {
        ensureProductExists(productId);
        return attributeTypeRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(AttributeTypeDetailResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AttributeTypeDetailResponse createAttributeType(Long productId, CreateAttributeTypeRequest request) {
        Product product = ensureProductExists(productId);

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

    @Override
    @Transactional
    public AttributeTypeDetailResponse createAttributeValue(Long attributeTypeId, CreateAttributeValueRequest request) {
        AttributeType type = attributeTypeRepository.findById(attributeTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attribute type not found: id=" + attributeTypeId));

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

        type = attributeTypeRepository.findById(attributeTypeId).orElseThrow();
        return AttributeTypeDetailResponse.from(type);
    }

    private Product ensureProductExists(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: id=" + productId));
    }
}
