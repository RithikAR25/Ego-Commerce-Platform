package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.CreateVariantRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateInventoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateVariantRequest;
import com.ego.raw_ego.catalog.dto.response.VariantResponse;
import com.ego.raw_ego.catalog.entity.*;
import com.ego.raw_ego.catalog.repository.*;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.search.entity.SearchOutboxEntry;
import com.ego.raw_ego.search.repository.SearchOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link ProductVariantService} — product variant management.
 *
 * <p><b>SKU generation (LOCKED):</b>
 * {@code EGO-{SUBCATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE_CODE}}
 * The SKU is set once and NEVER updated.
 *
 * <p>An {@link InventoryRecord} is created atomically with each variant.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final InventoryRecordRepository inventoryRepository;
    private final AttributeValueRepository  attributeValueRepository;
    private final ProductRepository         productRepository;
    private final ProductService            productService;
    private final SearchOutboxRepository    searchOutboxRepository;

    @Override
    @Transactional
    public VariantResponse createVariant(Long productId, CreateVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: id=" + productId));

        AttributeValue colorValue = findAndValidateAttributeValue(
                request.getColorAttributeValueId(), productId, "Color");
        AttributeValue sizeValue = findAndValidateAttributeValue(
                request.getSizeAttributeValueId(), productId, "Size");

        if (request.getCompareAtPrice() != null &&
            request.getCompareAtPrice().compareTo(request.getPrice()) <= 0) {
            throw new IllegalArgumentException("compareAtPrice must be strictly greater than price.");
        }

        String sku = generateSku(product, colorValue, sizeValue);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sku)
                .price(request.getPrice())
                .compareAtPrice(request.getCompareAtPrice())
                .costPrice(request.getCostPrice())
                .weightGrams(request.getWeightGrams())
                .active(true)
                .attributeValues(new ArrayList<>(List.of(colorValue, sizeValue)))
                .build();

        InventoryRecord inventory = InventoryRecord.builder()
                .variant(variant)
                .quantityAvailable(request.getInitialStock() != null ? request.getInitialStock() : 0)
                .quantityReserved(0)
                .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 5)
                .build();
        variant.setInventoryRecord(inventory);

        variant = variantRepository.save(variant);
        productService.syncProductStatusFromInventory(productId);

        log.info("Variant created: id={} sku={} product={}", variant.getId(), sku, productId);
        return VariantResponse.from(variant);
    }

    @Override
    @Transactional
    public VariantResponse updateVariant(Long variantId, UpdateVariantRequest request) {
        ProductVariant variant = findVariantById(variantId);

        if (request.getPrice() != null) {
            var compareTo = request.getCompareAtPrice() != null
                    ? request.getCompareAtPrice()
                    : variant.getCompareAtPrice();
            if (compareTo != null && compareTo.compareTo(request.getPrice()) <= 0) {
                throw new IllegalArgumentException("compareAtPrice must be greater than price.");
            }
            variant.setPrice(request.getPrice());
        }
        if (request.getCompareAtPrice() != null) variant.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getCostPrice() != null)       variant.setCostPrice(request.getCostPrice());
        if (request.getWeightGrams() != null)     variant.setWeightGrams(request.getWeightGrams());
        if (request.getActive() != null)          variant.setActive(request.getActive());

        variant = variantRepository.save(variant);

        if (request.getActive() != null) {
            productService.syncProductStatusFromInventory(variant.getProduct().getId());
        }

        publishOutbox(variant.getProduct().getId(), SearchOutboxEntry.EventType.UPSERT);

        log.info("Variant updated: id={} sku={}", variant.getId(), variant.getSku());
        return VariantResponse.from(variant);
    }

    @Override
    @Transactional
    public void updateInventory(Long variantId, UpdateInventoryRequest request) {
        InventoryRecord inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory record not found for variant: id=" + variantId));

        inventory.setQuantityAvailable(request.getQuantityAvailable());
        if (request.getLowStockThreshold() != null) {
            inventory.setLowStockThreshold(request.getLowStockThreshold());
        }

        inventoryRepository.save(inventory);
        productService.syncProductStatusFromInventory(inventory.getVariant().getProduct().getId());

        log.info("Inventory updated: variantId={} qty={}", variantId, request.getQuantityAvailable());
    }

    private void publishOutbox(Long productId, SearchOutboxEntry.EventType eventType) {
        searchOutboxRepository.save(
            SearchOutboxEntry.builder()
                .productId(productId)
                .eventType(eventType)
                .status(SearchOutboxEntry.Status.PENDING)
                .build()
        );
    }

    private String generateSku(Product product, AttributeValue colorValue, AttributeValue sizeValue) {
        String categoryCode = product.getCategory().getCode();
        String productCode  = product.getProductCode();
        String colorCode    = colorValue.getCode().toUpperCase();
        String sizeCode     = sizeValue.getCode().toUpperCase();

        String sku = String.format("EGO-%s-%s-%s-%s", categoryCode, productCode, colorCode, sizeCode);

        if (variantRepository.existsBySku(sku)) {
            throw new ConflictException("Variant with SKU '" + sku + "' already exists for this product.");
        }

        return sku;
    }

    private AttributeValue findAndValidateAttributeValue(Long valueId, Long productId, String typeName) {
        AttributeValue value = attributeValueRepository.findById(valueId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        typeName + " attribute value not found: id=" + valueId));

        Long attributeProductId = value.getAttributeType().getProduct().getId();
        if (!attributeProductId.equals(productId)) {
            throw new IllegalArgumentException(
                    typeName + " attribute value (id=" + valueId + ") does not belong to product id=" + productId);
        }

        return value;
    }

    private ProductVariant findVariantById(Long id) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: id=" + id));
    }
}
