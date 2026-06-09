package com.ego.raw_ego.order.repository;

import com.ego.raw_ego.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link OrderItem} entities.
 *
 * <p>Items are always accessed via the parent {@code Order} aggregate
 * (through the cascade relationship), so no custom queries are needed here.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
