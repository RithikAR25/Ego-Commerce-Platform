package com.ego.raw_ego.coupon.repository;

import com.ego.raw_ego.coupon.entity.OrderCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link OrderCoupon} audit records.
 */
public interface OrderCouponRepository extends JpaRepository<OrderCoupon, Long> {

    /** Find the coupon applied to a specific order (if any). */
    Optional<OrderCoupon> findByOrderId(Long orderId);
}
