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
 * Business logic for product variant management.
 *
 * <p><b>SKU generation (LOCKED):</b>
 * {@code EGO-{SUBCATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE_CODE}}
 * <ul>
 *   <li>Subcategory code comes from the product's category (e.g. "TEE")</li>
 *   <li>Product code comes from product.productCode (e.g. "0001")</li>
 *   <li>Color code from the color AttributeValue.code (e.g. "BLK")</li>
 *   <li>Size code from the size AttributeValue.code (e.g. "M")</li>
 * </ul>
 * The SKU is set once and NEVER updated.
 *
 * <p>An {@link InventoryRecord} is created atomically with each variant.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final InventoryRecordRepository inventoryRepository;
    private final AttributeValueRepository  attributeValueRepository;
    private final ProductRepository         productRepository;
    private final ProductService            productService;
    private final SearchOutboxRepository    searchOutboxRepository;

    // ── Admin operations ─────────────────────────────────────────────────────

    /**
     * Creates a new variant and its inventory record atomically.
     *
     * <p>Validation steps:
     * <ol>
     *   <li>Product exists</li>
     *   <li>Color + size AttributeValues belong to this product</li>
     *   <li>compareAtPrice > price (if provided)</li>
     *   <li>Generated SKU is unique</li>
     * </ol>
     */
    @Transactional
    public VariantResponse createVariant(Long productId, CreateVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: id=" + productId));

        // Resolve and validate attribute values
        AttributeValue colorValue = findAndValidateAttributeValue(
                request.getColorAttributeValueId(), productId, "Color");
        AttributeValue sizeValue = findAndValidateAttributeValue(
                request.getSizeAttributeValueId(), productId, "Size");

        // Validate compareAtPrice > price
        if (request.getCompareAtPrice() != null &&
            request.getCompareAtPrice().compareTo(request.getPrice()) <= 0) {
            throw new IllegalArgumentException(
                    "compareAtPrice must be strictly greater than price.");
        }

        // Generate SKU: EGO-{CAT}-{PROD}-{COLOR}-{SIZE}
        String sku = generateSku(product, colorValue, sizeValue);

        // Build and persist variant
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

        // Build and wire inventory BEFORE saving — cascade=ALL handles INSERT
        InventoryRecord inventory = InventoryRecord.builder()
                .variant(variant)
                .quantityAvailable(request.getInitialStock() != null ? request.getInitialStock() : 0)
                .quantityReserved(0)
                .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 5)
                .build();
        variant.setInventoryRecord(inventory);   // wire inverse side

        // Single save — CascadeType.ALL on inventoryRecord persists inventory atomically
        variant = variantRepository.save(variant);

        // Sync product status — if initialStock > 0 and product is DRAFT, leave as DRAFT
        // If product is OUT_OF_STOCK and stock > 0, auto-promote to ACTIVE
        productService.syncProductStatusFromInventory(productId);

        log.info("Variant created: id={} sku={} product={}", variant.getId(), sku, productId);
        return VariantResponse.from(variant);
    }

    /**
     * Updates variant pricing and/or active status.
     * SKU is NEVER updated — part of the immutable contract.
     */
    @Transactional
    public VariantResponse updateVariant(Long variantId, UpdateVariantRequest request) {
        ProductVariant variant = findVariantById(variantId);

        if (request.getPrice() != null) {
            // Re-validate compareAtPrice against new price
            var compareTo = request.getCompareAtPrice() != null
                    ? request.getCompareAtPrice()
                    : variant.getCompareAtPrice();
            if (compareTo != null && compareTo.compareTo(request.getPrice()) <= 0) {
                throw new IllegalArgumentException("compareAtPrice must be greater than price.");
            }
            variant.setPrice(request.getPrice());
        }
        if (request.getCompareAtPrice() != null) {
            variant.setCompareAtPrice(request.getCompareAtPrice());
        }
        if (request.getCostPrice() != null) {
            variant.setCostPrice(request.getCostPrice());
        }
        if (request.getWeightGrams() != null) {
            variant.setWeightGrams(request.getWeightGrams());
        }
        if (request.getActive() != null) {
            variant.setActive(request.getActive());
        }

        variant = variantRepository.save(variant);

        // Sync product status if active flag changed
        if (request.getActive() != null) {
            productService.syncProductStatusFromInventory(variant.getProduct().getId());
        }

        // Publish outbox event — price or active status changed, ES doc needs refresh
        publishOutbox(variant.getProduct().getId(), SearchOutboxEntry.EventType.UPSERT);

        log.info("Variant updated: id={} sku={}", variant.getId(), variant.getSku());
        return VariantResponse.from(variant);
    }

    /**
     * Sets the absolute inventory quantity for a variant (admin stock adjustment).
     * Syncs product status afterwards.
     */
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

        // Auto-sync product status
        productService.syncProductStatusFromInventory(inventory.getVariant().getProduct().getId());

        // Publish outbox event — stock changed, ES totalStock field needs refresh
        // (syncProductStatusFromInventory also publishes, but explicit here for clarity)
        log.info("Inventory updated: variantId={} qty={}", variantId, request.getQuantityAvailable());
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void publishOutbox(Long productId, SearchOutboxEntry.EventType eventType) {
        searchOutboxRepository.save(
            SearchOutboxEntry.builder()
                .productId(productId)
                .eventType(eventType)
                .status(SearchOutboxEntry.Status.PENDING)
                .build()
        );
    }

    /**
     * Generates the canonical SKU for a variant.
     * Format: EGO-{SUBCATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE_CODE}
     */
    private String generateSku(Product product, AttributeValue colorValue, AttributeValue sizeValue) {
        String categoryCode = product.getCategory().getCode();
        String productCode  = product.getProductCode();
        String colorCode    = colorValue.getCode().toUpperCase();
        String sizeCode     = sizeValue.getCode().toUpperCase();

        String sku = String.format("EGO-%s-%s-%s-%s", categoryCode, productCode, colorCode, sizeCode);

        if (variantRepository.existsBySku(sku)) {
            throw new ConflictException(
                    "Variant with SKU '" + sku + "' already exists for this product.");
        }

        return sku;
    }

    /**
     * Finds an AttributeValue by ID and validates it belongs to the given product.
     */
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
