package com.ego.raw_ego.review.repository;

import com.ego.raw_ego.review.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Repository for {@link ProductReview} entities.
 */
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    /**
     * Returns all reviews for a product, newest first.
     */
    Page<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    /**
     * Checks whether the given user already submitted a review for this product.
     * Used to return a clear 409 instead of relying on DB constraint violation.
     */
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /**
     * Returns the user's existing review for a product (for ownership checks on delete).
     */
    Optional<ProductReview> findByIdAndUserId(Long reviewId, Long userId);

    /**
     * Average rating for a product — used in the rating summary.
     * Returns null if no reviews exist yet.
     */
    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.productId = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    /**
     * Total review count for a product.
     */
    long countByProductId(Long productId);

    /**
     * Count of reviews at each star level (1–5) for a product.
     * Returns a list of [rating, count] pairs.
     */
    @Query("SELECT r.rating, COUNT(r) FROM ProductReview r WHERE r.productId = :productId GROUP BY r.rating")
    java.util.List<Object[]> findRatingBreakdownByProductId(@Param("productId") Long productId);

    /**
     * Eligibility check — has this user placed a DELIVERED order containing
     * any variant of the target product?
     *
     * <p>Bridges the cross-module boundary via raw FK:
     * {@code order_items.variant_id → product_variants.product_id}
     * without importing catalog or order entities directly.
     */
    @Query("""
            SELECT COUNT(oi) > 0
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.user.id = :userId
              AND o.status = com.ego.raw_ego.order.enums.OrderStatus.DELIVERED
              AND oi.variantId IN (
                  SELECT v.id FROM ProductVariant v WHERE v.product.id = :productId
              )
            """)
    boolean hasUserPurchasedProduct(@Param("userId") Long userId,
                                    @Param("productId") Long productId);

    /**
     * Deletes all reviews for a product — called as part of the product hard-delete
     * cleanup sequence, before the product row itself is removed.
     *
     * <p>Must run within a transaction (ensured by the calling service).
     */
    @Transactional
    void deleteByProductId(Long productId);
}
