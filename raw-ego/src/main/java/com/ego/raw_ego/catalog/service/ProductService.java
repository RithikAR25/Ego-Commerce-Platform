package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.CreateProductRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateProductStatusRequest;
import com.ego.raw_ego.catalog.dto.response.ProductDetailResponse;
import com.ego.raw_ego.catalog.dto.response.ProductSummaryResponse;
import com.ego.raw_ego.catalog.entity.Category;
import com.ego.raw_ego.catalog.entity.Product;
import com.ego.raw_ego.catalog.enums.ProductStatus;
import com.ego.raw_ego.catalog.event.ProductStockStatusChangedEvent;
import com.ego.raw_ego.catalog.repository.ProductRepository;
import com.ego.raw_ego.catalog.repository.ProductVariantRepository;
import com.ego.raw_ego.common.exception.BusinessRuleViolationException;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.util.SlugUtils;
import com.ego.raw_ego.review.repository.ProductReviewRepository;
import com.ego.raw_ego.search.entity.SearchOutboxEntry;
import com.ego.raw_ego.search.repository.SearchOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for product lifecycle management.
 *
 * <p><b>Category rule:</b> A product must always belong to a LEAF category (depth=2).
 * ROOT and GROUP categories are invalid assignment targets. Enforced here.
 *
 * <p><b>Product code generation:</b> Sequential zero-padded 4-digit code.
 * "0001", "0002", ..., "9999". Derived from MAX(product_code) + 1.
 * If the table is empty, starts at "0001".
 *
 * <p><b>Status transitions (auto-managed):</b>
 * {@link #syncProductStatusFromInventory(Long)} is called by {@link ProductVariantService}
 * after any inventory change to ensure status reflects actual stock.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private static final List<ProductStatus> PUBLIC_STATUSES =
            List.of(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK);

    private final ProductRepository           productRepository;
    private final ProductVariantRepository    variantRepository;
    private final CategoryService             categoryService;
    private final ProductReviewRepository     reviewRepository;
    private final SearchOutboxRepository      searchOutboxRepository;
    private final ApplicationEventPublisher   eventPublisher;
    private final StockUrgencyService         stockUrgencyService;

    // ── Public (storefront) ──────────────────────────────────────────────────

    /**
     * Paginated product listing for the storefront — returns ACTIVE and OUT_OF_STOCK only.
     */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getPublicProducts(Pageable pageable) {
        return productRepository
                .findByStatusInOrderByCreatedAtDesc(PUBLIC_STATUSES, pageable)
                .map(ProductSummaryResponse::from);
    }

    /**
     * Products filtered by subcategory slug — for category pages.
     */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getProductsByCategory(String categorySlug, Pageable pageable) {
        Category category = categoryService.findById(
                categoryService.getCategoryBySlug(categorySlug).getId()
        );
        return productRepository
                .findByCategoryIdAndStatusIn(category.getId(), PUBLIC_STATUSES, pageable)
                .map(ProductSummaryResponse::from);
    }

    /**
     * Full product detail by slug — for the Product Detail Page.
     * Loads all active variants with attributes, images, and stock urgency fields.
     */
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatusIn(slug, PUBLIC_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        return ProductDetailResponse.from(product, stockUrgencyService);
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    /**
     * Admin product listing — all statuses, all products.
     */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getAllProductsAdmin(Pageable pageable) {
        return productRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(ProductSummaryResponse::from);
    }

    /**
     * Admin full detail — returns product regardless of status.
     * Also enriched with stock urgency for consistent admin preview behaviour.
     */
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlugAdmin(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        return ProductDetailResponse.from(product, stockUrgencyService);
    }

    /**
     * Creates a new product in DRAFT status.
     *
     * <p>Validations:
     * <ul>
     *   <li>Category must be a LEAF category (depth=2) — ROOT and GROUP are rejected</li>
     *   <li>Slug is auto-generated and guaranteed unique</li>
     *   <li>Product code is auto-generated as next sequential value</li>
     * </ul>
     */
    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request) {
        Category category = categoryService.findById(request.getCategoryId());

        if (!category.isLeaf()) {
            String level = category.isRoot() ? "ROOT" : "GROUP";
            throw new IllegalArgumentException(
                    "Products must be assigned to a LEAF category (e.g. \"T-Shirts\", \"Jeans\"). " +
                    "Category '" + category.getName() + "' is a " + level + " category. " +
                    "Select a leaf-level category under it.");
        }

        String slug = SlugUtils.toUniqueSlug(request.getName(), productRepository::existsBySlug);
        String productCode = generateNextProductCode();

        Product product = Product.builder()
                .name(request.getName().trim())
                .category(category)
                .slug(slug)
                .productCode(productCode)
                .description(request.getDescription())
                .material(request.getMaterial())
                .careInstructions(request.getCareInstructions())
                .tags(request.getTags())
                .status(ProductStatus.DRAFT)
                .build();

        product = productRepository.save(product);
        log.info("Product created: id={} slug={} code={} category={}",
                product.getId(), product.getSlug(), product.getProductCode(), category.getName());

        return ProductDetailResponse.from(product);
    }

    /**
     * Admin-triggered status change. Validates the transition is legal.
     *
     * <p>Note: OUT_OF_STOCK ↔ ACTIVE transitions are managed automatically
     * by {@link #syncProductStatusFromInventory(Long)} and should not be manually set.
     */
    @Transactional
    public ProductDetailResponse updateStatus(Long id, UpdateProductStatusRequest request) {
        Product product = findProductById(id);
        ProductStatus current = product.getStatus();
        ProductStatus target  = request.getStatus();

        validateStatusTransition(current, target);

        productRepository.updateStatus(id, target);
        product.setStatus(target);

        // Write outbox event in same transaction — guarantees durable ES sync
        if (target == ProductStatus.ACTIVE || target == ProductStatus.OUT_OF_STOCK) {
            publishOutbox(id, SearchOutboxEntry.EventType.UPSERT);
        } else if (target == ProductStatus.ARCHIVED) {
            publishOutbox(id, SearchOutboxEntry.EventType.DELETE);
        }

        log.info("Product status updated: id={} {} → {}", id, current, target);
        return ProductDetailResponse.from(product, stockUrgencyService);
    }

    /**
     * Archives a product — admin soft-delete.
     * A product with active orders cannot be archived (Phase 6 will add this guard).\
     */
    @Transactional
    public void archiveProduct(Long id) {
        Product product = findProductById(id);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new IllegalArgumentException("Product is already archived.");
        }
        productRepository.updateStatus(id, ProductStatus.ARCHIVED);
        log.info("Product archived: id={}", id);
    }

    /**
     * Soft-deletes a product by setting {@code isDeleted=true}.
     *
     * <p>This is the preferred admin deletion path. The product disappears from
     * all storefront queries immediately (via {@code @SQLRestriction}) but its row
     * remains in the database, preserving order history integrity.
     *
     * <p>An ES {@code DELETE} outbox entry is published in the same transaction
     * so the product is removed from the search index within 5 seconds.
     *
     * @param id product primary key
     * @throws ResourceNotFoundException if no product with this id exists
     */
    @Transactional
    public void softDeleteProduct(Long id) {
        Product product = findProductById(id);

        product.setDeleted(true);
        product.setStatus(ProductStatus.ARCHIVED);
        productRepository.save(product);

        // Publish ES DELETE outbox event in same transaction — durable removal from search index
        publishOutbox(id, SearchOutboxEntry.EventType.DELETE);

        log.info("Product soft-deleted: id={} name='{}'", id, product.getName());
    }

    /**
     * Permanently (hard) deletes an archived product and ALL related data.
     *
     * <p><b>Guards (checked in order):</b>
     * <ol>
     *   <li>Product must be {@link ProductStatus#ARCHIVED}.</li>
     *   <li>No {@code order_items} rows may reference any variant of this product.
     *       Order history is a legal record and must not be orphaned.</li>
     * </ol>
     *
     * <p><b>Cleanup sequence (within a single transaction):</b>
     * <ol>
     *   <li>Purge all {@link com.ego.raw_ego.search.entity.SearchOutboxEntry} rows
     *       — prevents the outbox poller from processing stale events post-delete.</li>
     *   <li>Delete all {@link com.ego.raw_ego.review.entity.ProductReview} rows
     *       — {@code productId} is a raw column FK with no JPA cascade.</li>
     *   <li>Delete the {@link Product} entity — JPA {@code CascadeType.ALL} +
     *       {@code orphanRemoval=true} on {@code variants}, {@code images}, and
     *       {@code attributeTypes} cascades the remaining rows automatically.</li>
     * </ol>
     *
     * @param id product primary key
     * @throws ResourceNotFoundException if no product with this id exists
     * @throws ConflictException         if status is not ARCHIVED, or if order history exists
     */
    @Transactional
    public void hardDeleteProduct(Long id) {
        Product product = findProductById(id);

        // Guard 1 — must already be archived
        if (product.getStatus() != ProductStatus.ARCHIVED) {
            throw new ConflictException(
                    "Product must be ARCHIVED before it can be permanently deleted. " +
                    "Current status: " + product.getStatus());
        }

        // Guard 2 — must have no order history
        long orderCount = productRepository.countOrderItemsByProductId(id);
        if (orderCount > 0) {
            throw new ConflictException(
                    "Cannot permanently delete a product that has order history (" +
                    orderCount + " order item(s) found). Archived products with orders " +
                    "must be kept for record integrity.");
        }

        // Cleanup 1 — purge stale outbox entries
        searchOutboxRepository.deleteByProductId(id);

        // Cleanup 2 — delete reviews (raw FK, no JPA cascade)
        reviewRepository.deleteByProductId(id);

        // Delete — JPA cascade handles variants, variant_images, product_images,
        //           attribute_types, and attribute_values automatically
        productRepository.deleteById(id);

        log.info("Product permanently deleted: id={} name='{}'", id, product.getName());
    }

    /**
     * Called by {@link ProductVariantService} after any inventory change.
     * Automatically transitions:
     * <ul>
     *   <li>ACTIVE → OUT_OF_STOCK when all variant stock hits 0</li>
     *   <li>OUT_OF_STOCK → ACTIVE when any variant is restocked</li>
     * </ul>
     * DRAFT and ARCHIVED products are not touched.
     */
    @Transactional
    public void syncProductStatusFromInventory(Long productId) {
        Product product = findProductById(productId);
        ProductStatus current = product.getStatus();

        if (current == ProductStatus.DRAFT || current == ProductStatus.ARCHIVED) {
            return; // Only manage ACTIVE ↔ OUT_OF_STOCK automatically
        }

        long variantsWithStock = variantRepository.countActiveVariantsWithStock(productId);

        if (variantsWithStock == 0 && current == ProductStatus.ACTIVE) {
            productRepository.updateStatus(productId, ProductStatus.OUT_OF_STOCK);
            log.info("Product auto-transitioned ACTIVE → OUT_OF_STOCK: id={}", productId);
            // Notify wishlisted users — async, decoupled via Spring event
            eventPublisher.publishEvent(new ProductStockStatusChangedEvent(
                    this, productId, product.getName(), ProductStatus.OUT_OF_STOCK));
        } else if (variantsWithStock > 0 && current == ProductStatus.OUT_OF_STOCK) {
            productRepository.updateStatus(productId, ProductStatus.ACTIVE);
            log.info("Product auto-transitioned OUT_OF_STOCK → ACTIVE: id={}", productId);
            // Notify wishlisted users — async, decoupled via Spring event
            eventPublisher.publishEvent(new ProductStockStatusChangedEvent(
                    this, productId, product.getName(), ProductStatus.ACTIVE));
        }

        // Always sync stock count in ES even if status didn't change
        // (covers inventory adjustments that don't trigger a status transition)
        if (current == ProductStatus.ACTIVE || current == ProductStatus.OUT_OF_STOCK) {
            publishOutbox(productId, SearchOutboxEntry.EventType.UPSERT);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Writes a durable outbox event within the current transaction.
     * The outbox poller will process it asynchronously within 5 seconds.
     */
    private void publishOutbox(Long productId, SearchOutboxEntry.EventType eventType) {
        searchOutboxRepository.save(
            SearchOutboxEntry.builder()
                .productId(productId)
                .eventType(eventType)
                .status(SearchOutboxEntry.Status.PENDING)
                .build()
        );
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: id=" + id));
    }

    private static final int MAX_PRODUCT_CODE = 9999;

    /**
     * Generates the next zero-padded 4-digit product code.
     * MAX("0042") + 1 = "0043". If no products exist, starts at "0001".
     */
    private String generateNextProductCode() {
        String maxCode = productRepository.findMaxProductCode().orElse("0000");
        int next = Integer.parseInt(maxCode) + 1;
        if (next > MAX_PRODUCT_CODE) {
            throw new BusinessRuleViolationException(
                "Product code limit reached (" + MAX_PRODUCT_CODE + "). Contact the platform administrator.");
        }
        return String.format("%04d", next);
    }

    private void validateStatusTransition(ProductStatus from, ProductStatus to) {
        boolean valid = switch (from) {
            case DRAFT        -> to == ProductStatus.ACTIVE || to == ProductStatus.ARCHIVED;
            case ACTIVE       -> to == ProductStatus.ARCHIVED;
            case OUT_OF_STOCK -> to == ProductStatus.ARCHIVED;
            case ARCHIVED     -> to == ProductStatus.DRAFT;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + from + " → " + to);
        }
    }
}
