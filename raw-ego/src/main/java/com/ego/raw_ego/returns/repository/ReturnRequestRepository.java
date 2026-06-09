package com.ego.raw_ego.returns.repository;

import com.ego.raw_ego.returns.entity.ReturnRequest;
import com.ego.raw_ego.returns.enums.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link ReturnRequest} entities.
 *
 * <p>Ownership enforcement pattern (mirrors OrderRepository):
 * Customer-facing queries use {@code findByIdAndRequestedById} to prevent
 * return ID enumeration. Non-owners receive a 404 (not a 403).
 */
@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    /**
     * Returns the return request for a specific order if it belongs to the given user.
     * Used by: {@code GET /api/v1/orders/{orderId}/returns}
     */
    Optional<ReturnRequest> findByOrderIdAndRequestedById(Long orderId, Long userId);

    /**
     * Returns any return request for a given order (admin view — no ownership filter).
     * Used by: {@code GET /api/v1/admin/returns/{returnId}} and internal lookups.
     */
    Optional<ReturnRequest> findByOrderId(Long orderId);

    /**
     * Checks whether an active (non-rejected) return request already exists for an order.
     * Used to prevent duplicate return submissions on the same order.
     *
     * <p>Only one non-rejected return is allowed per order. If the previous request was
     * REJECTED, the customer may submit a new one.
     *
     * @param orderId the order to check
     * @param status  the status to exclude (pass {@link ReturnStatus#REJECTED})
     * @return true if a return in any status other than {@code status} exists
     */
    boolean existsByOrderIdAndStatusNot(Long orderId, ReturnStatus status);

    /**
     * Count of return requests in a specific status.
     * Used by the dashboard KPI aggregation to compute the "Pending Returns" card value.
     * Callers pass {@link ReturnStatus#REQUESTED} to count returns awaiting admin review.
     *
     * <p>Spring Data derives: {@code SELECT COUNT(r) FROM ReturnRequest r WHERE r.status = :status}.
     */
    long countByStatus(ReturnStatus status);

    /**
     * Admin: all return requests, optionally filtered by status, newest first.
     * Used by: {@code GET /api/v1/admin/returns?status=...}
     *
     * <p>When {@code status} is null, returns all return requests.
     */
    @Query("""
            SELECT r FROM ReturnRequest r
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    Page<ReturnRequest> findAllByStatusFilter(ReturnStatus status, Pageable pageable);
}
