package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.Product;
import com.ego.raw_ego.catalog.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySlug(String slug);

    boolean existsByProductCode(String productCode);

    /**
     * True if any product (any status) is assigned to the given category.
     * Used as a hard-delete guard — a category cannot be permanently deleted
     * while products still reference it.
     */
    boolean existsByCategory_Id(Long categoryId);

    /**
     * Returns product counts grouped by category ID in a single query.
     *
     * <p>Each element of the result list is an {@code Object[2]}:
     * <ul>
     *   <li>{@code row[0]} — {@code Long} category ID</li>
     *   <li>{@code row[1]} — {@code Long} product count (any status)</li>
     * </ul>
     *
     * <p>Used by {@code CategoryService.getAllCategoriesAdmin()} to populate
     * {@code CategoryResponse.productCount} without N+1 queries.
     * A single {@code GROUP BY} is far more efficient than calling
     * {@code countByCategory_Id()} once per category.
     */
    @Query("SELECT p.category.id, COUNT(p) FROM Product p GROUP BY p.category.id")
    List<Object[]> countProductsGroupedByCategoryId();

    /** Storefront: product detail page — public, ACTIVE or OUT_OF_STOCK only. */
    Optional<Product> findBySlugAndStatusIn(String slug, java.util.List<ProductStatus> statuses);

    /** Admin: find by slug regardless of status. */
    Optional<Product> findBySlug(String slug);

    /** Storefront: paginated listing for a specific subcategory (public-visible statuses). */
    @Query("""
        SELECT p FROM Product p
        WHERE p.category.id = :categoryId
          AND p.status IN :statuses
        ORDER BY p.createdAt DESC
        """)
    Page<Product> findByCategoryIdAndStatusIn(Long categoryId,
                                               java.util.List<ProductStatus> statuses,
                                               Pageable pageable);

    /** Storefront: paginated listing of all visible products (ACTIVE + OUT_OF_STOCK). */
    Page<Product> findByStatusInOrderByCreatedAtDesc(java.util.List<ProductStatus> statuses,
                                                     Pageable pageable);

    /** Admin: paginated listing of all products. */
    Page<Product> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Find the highest existing product_code to generate the next sequential one. */
    @Query("SELECT MAX(p.productCode) FROM Product p")
    Optional<String> findMaxProductCode();

    /** Targeted status update — avoids bumping the optimistic lock version. */
    @Modifying
    @Query("UPDATE Product p SET p.status = :status WHERE p.id = :id")
    int updateStatus(Long id, ProductStatus status);

    /**
     * Counts order-item rows whose variant belongs to the given product.
     *
     * <p>Used as a hard-delete guard: a product cannot be permanently deleted
     * while any of its variants have been ordered. Order items are a legal
     * record and must never be orphaned.
     *
     * <p>Uses the cross-module JPQL pattern already established in
     * {@code ProductReviewRepository.hasUserPurchasedProduct}: JPQL refers
     * to {@code OrderItem} by entity name, and accesses the raw
     * {@code variantId} mapped column directly.
     */
    @Query("""
        SELECT COUNT(oi) FROM OrderItem oi
        WHERE oi.variantId IN (
            SELECT v.id FROM ProductVariant v WHERE v.product.id = :productId
        )
        """)
    long countOrderItemsByProductId(@Param("productId") Long productId);
}
