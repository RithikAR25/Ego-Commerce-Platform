package com.ego.raw_ego.coupon.repository;

import com.ego.raw_ego.coupon.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for {@link Coupon} entities.
 */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Find an active coupon by code (case-insensitive).
     * Codes are stored UPPER — normalisation done at service layer before lookup.
     */
    Optional<Coupon> findByCodeAndActiveTrue(String code);

    /** Check if a code already exists (for uniqueness validation on create). */
    boolean existsByCode(String code);

    /** Admin paginated list — all coupons including inactive. */
    Page<Coupon> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Atomically increment current_uses for a coupon.
     * Called inside the checkout transaction to prevent over-use under concurrency.
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.currentUses = c.currentUses + 1 WHERE c.id = :couponId")
    void incrementUses(@Param("couponId") Long couponId);
}
