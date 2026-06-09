package com.ego.raw_ego.search.repository;

import com.ego.raw_ego.search.entity.SearchOutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA repository for the {@link SearchOutboxEntry} table.
 *
 * <p>The primary query is {@link #findPendingBatch} — used by the
 * {@link com.ego.raw_ego.search.job.OutboxPoller} every 5 seconds.
 */
@Repository
public interface SearchOutboxRepository extends JpaRepository<SearchOutboxEntry, Long> {

    /**
     * Fetches the oldest PENDING outbox entries up to the given limit.
     *
     * <p>Ordered by {@code createdAt ASC} ensures FIFO processing — events
     * from early mutations are indexed before later ones, maintaining causal order.
     *
     * @param status must be {@code Status.PENDING}
     * @param limit  max rows to return per poll cycle
     */
    @Query("""
        SELECT e FROM SearchOutboxEntry e
        WHERE e.status = :status
        ORDER BY e.createdAt ASC
        LIMIT :limit
        """)
    List<SearchOutboxEntry> findPendingBatch(
            @Param("status") SearchOutboxEntry.Status status,
            @Param("limit")  int limit);

    /**
     * Counts FAILED entries — used in health reporting and admin monitoring.
     */
    long countByStatus(SearchOutboxEntry.Status status);

    /**
     * Returns all entries with the given status — used by the admin DLQ viewer
     * ({@code GET /api/v1/admin/search/outbox/failed}).
     */
    List<SearchOutboxEntry> findByStatus(SearchOutboxEntry.Status status);

    /**
     * Deletes all outbox entries for a product — called as the first step of
     * the product hard-delete cleanup sequence so the outbox poller cannot
     * attempt to process stale events after the product row is removed.
     */
    @Transactional
    void deleteByProductId(Long productId);
}
