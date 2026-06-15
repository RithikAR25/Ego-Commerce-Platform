package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.CreateProductRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateProductStatusRequest;
import com.ego.raw_ego.catalog.dto.response.ProductDetailResponse;
import com.ego.raw_ego.catalog.dto.response.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for product lifecycle management.
 *
 * <p><b>Category rule:</b> A product must always belong to a LEAF category (depth=2).
 *
 * <p><b>Status transitions (auto-managed):</b>
 * {@link #syncProductStatusFromInventory(Long)} is called by {@link ProductVariantService}
 * after any inventory change to ensure status reflects actual stock.
 */
public interface ProductService {

    /** Paginated product listing for the storefront — returns ACTIVE and OUT_OF_STOCK only. */
    Page<ProductSummaryResponse> getPublicProducts(Pageable pageable);

    /** Products filtered by category slug — for category pages. */
    Page<ProductSummaryResponse> getProductsByCategory(String categorySlug, Pageable pageable);

    /** Full product detail by slug — for the Product Detail Page. */
    ProductDetailResponse getProductBySlug(String slug);

    /** Admin product listing — all statuses, all products. */
    Page<ProductSummaryResponse> getAllProductsAdmin(Pageable pageable);

    /** Admin full detail — returns product regardless of status. */
    ProductDetailResponse getProductBySlugAdmin(String slug);

    /**
     * Creates a new product in DRAFT status.
     *
     * @throws IllegalArgumentException if category is not LEAF
     */
    ProductDetailResponse createProduct(CreateProductRequest request);

    /**
     * Admin-triggered status change. Validates the transition is legal.
     */
    ProductDetailResponse updateStatus(Long id, UpdateProductStatusRequest request);

    /** Archives a product — admin soft-delete. */
    void archiveProduct(Long id);

    /**
     * Soft-deletes a product by setting {@code isDeleted=true}.
     * Publishes ES DELETE outbox event in the same transaction.
     */
    void softDeleteProduct(Long id);

    /**
     * Permanently deletes an archived product and ALL related data.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if not ARCHIVED or has order history
     */
    void hardDeleteProduct(Long id);

    /**
     * Called after any inventory change to auto-manage ACTIVE ↔ OUT_OF_STOCK transitions.
     * DRAFT and ARCHIVED products are not touched.
     */
    void syncProductStatusFromInventory(Long productId);
}
