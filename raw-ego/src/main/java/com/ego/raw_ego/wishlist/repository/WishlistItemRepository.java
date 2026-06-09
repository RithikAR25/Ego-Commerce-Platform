package com.ego.raw_ego.wishlist.repository;

import com.ego.raw_ego.wishlist.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link WishlistItem} entities.
 */
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    /** All wishlist items for a user, oldest first. */
    List<WishlistItem> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** Check if a variant is already in the user's wishlist. */
    boolean existsByUserIdAndVariantId(Long userId, Long variantId);

    /** Find a specific wishlist item by user + variant (for idempotent remove). */
    Optional<WishlistItem> findByUserIdAndVariantId(Long userId, Long variantId);

    /** Delete all wishlist items for a user (clear wishlist). */
    void deleteAllByUserId(Long userId);

    /** Count of items in a user's wishlist. */
    long countByUserId(Long userId);

    /**
     * Returns the distinct user IDs that have any variant of the given product in their wishlist.
     *
     * <p>Used by the {@link com.ego.raw_ego.wishlist.service.WishlistStockNotificationListener}
     * to fan out stock-change emails to all affected users without iterating every variant.
     *
     * @param productId the product whose wishlist subscribers should be found
     */
    @Query("""
        SELECT DISTINCT wi.userId
        FROM WishlistItem wi
        JOIN ProductVariant v ON v.id = wi.variantId
        WHERE v.product.id = :productId
        """)
    List<Long> findUserIdsByProductId(@Param("productId") Long productId);
}
