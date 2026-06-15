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
 * Implementation of {@link ProductService} — business logic for product lifecycle management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final List<ProductStatus> PUBLIC_STATUSES =
            List.of(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK);
    private static final int MAX_PRODUCT_CODE = 9999;

    private final ProductRepository           productRepository;
    private final ProductVariantRepository    variantRepository;
    private final CategoryService             categoryService;
    private final ProductReviewRepository     reviewRepository;
    private final SearchOutboxRepository      searchOutboxRepository;
    private final ApplicationEventPublisher   eventPublisher;
    private final StockUrgencyService         stockUrgencyService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getPublicProducts(Pageable pageable) {
        return productRepository
                .findByStatusInOrderByCreatedAtDesc(PUBLIC_STATUSES, pageable)
                .map(ProductSummaryResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getProductsByCategory(String categorySlug, Pageable pageable) {
        Category category = categoryService.findById(
                categoryService.getCategoryBySlug(categorySlug).getId()
        );
        return productRepository
                .findByCategoryIdAndStatusIn(category.getId(), PUBLIC_STATUSES, pageable)
                .map(ProductSummaryResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndStatusIn(slug, PUBLIC_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        return ProductDetailResponse.from(product, stockUrgencyService);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getAllProductsAdmin(Pageable pageable) {
        return productRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(ProductSummaryResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlugAdmin(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        return ProductDetailResponse.from(product, stockUrgencyService);
    }

    @Override
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

    @Override
    @Transactional
    public ProductDetailResponse updateStatus(Long id, UpdateProductStatusRequest request) {
        Product product = findProductById(id);
        ProductStatus current = product.getStatus();
        ProductStatus target  = request.getStatus();

        validateStatusTransition(current, target);

        productRepository.updateStatus(id, target);
        product.setStatus(target);

        if (target == ProductStatus.ACTIVE || target == ProductStatus.OUT_OF_STOCK) {
            publishOutbox(id, SearchOutboxEntry.EventType.UPSERT);
        } else if (target == ProductStatus.ARCHIVED) {
            publishOutbox(id, SearchOutboxEntry.EventType.DELETE);
        }

        log.info("Product status updated: id={} {} → {}", id, current, target);
        return ProductDetailResponse.from(product, stockUrgencyService);
    }

    @Override
    @Transactional
    public void archiveProduct(Long id) {
        Product product = findProductById(id);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw new IllegalArgumentException("Product is already archived.");
        }
        productRepository.updateStatus(id, ProductStatus.ARCHIVED);
        log.info("Product archived: id={}", id);
    }

    @Override
    @Transactional
    public void softDeleteProduct(Long id) {
        Product product = findProductById(id);
        product.setDeleted(true);
        product.setStatus(ProductStatus.ARCHIVED);
        productRepository.save(product);
        publishOutbox(id, SearchOutboxEntry.EventType.DELETE);
        log.info("Product soft-deleted: id={} name='{}'", id, product.getName());
    }

    @Override
    @Transactional
    public void hardDeleteProduct(Long id) {
        Product product = findProductById(id);

        if (product.getStatus() != ProductStatus.ARCHIVED) {
            throw new ConflictException(
                    "Product must be ARCHIVED before it can be permanently deleted. " +
                    "Current status: " + product.getStatus());
        }

        long orderCount = productRepository.countOrderItemsByProductId(id);
        if (orderCount > 0) {
            throw new ConflictException(
                    "Cannot permanently delete a product that has order history (" +
                    orderCount + " order item(s) found). Archived products with orders " +
                    "must be kept for record integrity.");
        }

        searchOutboxRepository.deleteByProductId(id);
        reviewRepository.deleteByProductId(id);
        productRepository.deleteById(id);

        log.info("Product permanently deleted: id={} name='{}'", id, product.getName());
    }

    @Override
    @Transactional
    public void syncProductStatusFromInventory(Long productId) {
        Product product = findProductById(productId);
        ProductStatus current = product.getStatus();

        if (current == ProductStatus.DRAFT || current == ProductStatus.ARCHIVED) {
            return;
        }

        long variantsWithStock = variantRepository.countActiveVariantsWithStock(productId);

        if (variantsWithStock == 0 && current == ProductStatus.ACTIVE) {
            productRepository.updateStatus(productId, ProductStatus.OUT_OF_STOCK);
            log.info("Product auto-transitioned ACTIVE → OUT_OF_STOCK: id={}", productId);
            eventPublisher.publishEvent(new ProductStockStatusChangedEvent(
                    this, productId, product.getName(), ProductStatus.OUT_OF_STOCK));
        } else if (variantsWithStock > 0 && current == ProductStatus.OUT_OF_STOCK) {
            productRepository.updateStatus(productId, ProductStatus.ACTIVE);
            log.info("Product auto-transitioned OUT_OF_STOCK → ACTIVE: id={}", productId);
            eventPublisher.publishEvent(new ProductStockStatusChangedEvent(
                    this, productId, product.getName(), ProductStatus.ACTIVE));
        }

        if (current == ProductStatus.ACTIVE || current == ProductStatus.OUT_OF_STOCK) {
            publishOutbox(productId, SearchOutboxEntry.EventType.UPSERT);
        }
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

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: id=" + id));
    }

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
            throw new IllegalArgumentException("Invalid status transition: " + from + " → " + to);
        }
    }
}
