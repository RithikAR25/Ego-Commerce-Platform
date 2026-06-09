package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.InventoryRecord;
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
public interface InventoryRecordRepository extends JpaRepository<InventoryRecord, Long> {

    Optional<InventoryRecord> findByVariantId(Long variantId);

    /** All inventory records for a product — for admin inventory overview. */
    @Query("SELECT ir FROM InventoryRecord ir WHERE ir.variant.product.id = :productId")
    List<InventoryRecord> findAllByProductId(Long productId);

    /** Low-stock items across all variants — for admin alerts (Phase 8). */
    @Query("""
        SELECT ir FROM InventoryRecord ir
        WHERE ir.quantityAvailable <= ir.lowStockThreshold
          AND ir.quantityAvailable > 0
        """)
    List<InventoryRecord> findLowStockRecords();

    /**
     * Paginated inventory list with optional status and search filters.
     *
     * <p>Status filtering:
     * <ul>
     *   <li>"OUT_OF_STOCK" → quantityAvailable = 0</li>
     *   <li>"LOW_STOCK"    → 0 < quantityAvailable <= lowStockThreshold</li>
     *   <li>"IN_STOCK"     → quantityAvailable > lowStockThreshold</li>
     *   <li>null           → no status filter (all variants)</li>
     * </ul>
     *
     * <p>Search filters on SKU or product name (case-insensitive LIKE).
     */
    @Query(value = """
        SELECT ir FROM InventoryRecord ir
        JOIN FETCH ir.variant v
        JOIN FETCH v.product p
        LEFT JOIN FETCH v.attributeValues av
        WHERE (
            :status IS NULL OR (
                (:status = 'OUT_OF_STOCK' AND ir.quantityAvailable = 0) OR
                (:status = 'LOW_STOCK'    AND ir.quantityAvailable > 0 AND ir.quantityAvailable <= ir.lowStockThreshold) OR
                (:status = 'IN_STOCK'     AND ir.quantityAvailable > ir.lowStockThreshold)
            )
        )
        AND (
            :search IS NULL
            OR LOWER(v.sku)   LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.name)  LIKE LOWER(CONCAT('%', :search, '%'))
        )
        """,
        countQuery = """
        SELECT COUNT(ir) FROM InventoryRecord ir
        JOIN ir.variant v
        JOIN v.product p
        WHERE (
            :status IS NULL OR (
                (:status = 'OUT_OF_STOCK' AND ir.quantityAvailable = 0) OR
                (:status = 'LOW_STOCK'    AND ir.quantityAvailable > 0 AND ir.quantityAvailable <= ir.lowStockThreshold) OR
                (:status = 'IN_STOCK'     AND ir.quantityAvailable > ir.lowStockThreshold)
            )
        )
        AND (
            :search IS NULL
            OR LOWER(v.sku)   LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.name)  LIKE LOWER(CONCAT('%', :search, '%'))
        )
        """)
    Page<InventoryRecord> findAllWithFilters(
        @Param("status") String status,
        @Param("search") String search,
        Pageable pageable
    );

    /**
     * Count of inventory records currently in low-stock state.
     * Used by the dashboard KPI aggregation ({@code GET /api/v1/admin/dashboard/summary})
     * to compute the "Low Stock Alerts" card value.
     *
     * <p>Low stock = quantity_available ≤ low_stock_threshold AND quantity_available > 0.
     * Items with quantity = 0 are OUT_OF_STOCK, not LOW_STOCK, and are excluded.
     */
    @Query("""
        SELECT COUNT(ir) FROM InventoryRecord ir
        WHERE ir.quantityAvailable <= ir.lowStockThreshold
          AND ir.quantityAvailable > 0
        """)
    long countLowStockItems();

    /**
     * Targeted increment of quantityAvailable using optimistic lock version bump.
     * Returns number of rows updated (0 = version conflict → retry or fail).
     *
     * <p>{@code clearAutomatically = true} is REQUIRED: the bulk JPQL UPDATE bypasses
     * Hibernate's first-level entity cache. Without clearing, any subsequent
     * {@link #findByVariantId} within the same transaction returns the stale
     * pre-update entity (wrong version number), causing all following optimistic
     * lock checks to fail permanently.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE InventoryRecord ir
        SET ir.quantityAvailable = ir.quantityAvailable + :delta,
            ir.version = ir.version + 1
        WHERE ir.id = :id
          AND ir.version = :expectedVersion
          AND ir.quantityAvailable + :delta >= 0
        """)
    int adjustQuantityWithOptimisticLock(Long id, int delta, int expectedVersion);

    /**
     * Safely increments (or decrements) {@code quantity_reserved} with an optimistic lock check.
     * Used by {@link com.ego.raw_ego.cart.service.InventoryReservationService} during
     * cart add (positive delta) and cart remove / TTL expiry (negative delta).
     *
     * <p>Returns the number of rows updated:
     * <ul>
     *   <li>{@code 1} — success</li>
     *   <li>{@code 0} — version conflict (concurrent modification) or constraint violation</li>
     * </ul>
     *
     * <p>The {@code quantity_reserved + :delta >= 0} guard prevents the reserved count
     * from going negative on concurrent remove operations.
     *
     * <p>{@code clearAutomatically = true}: same reason as {@link #adjustQuantityWithOptimisticLock}
     * — clears the first-level cache so the retry loop in
     * {@code InventoryReservationService.adjustReservedWithRetry} reads a fresh version number.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE InventoryRecord ir
        SET ir.quantityReserved = ir.quantityReserved + :delta,
            ir.version = ir.version + 1
        WHERE ir.id = :id
          AND ir.version = :expectedVersion
          AND ir.quantityReserved + :delta >= 0
        """)
    int adjustReservedWithOptimisticLock(Long id, int delta, int expectedVersion);
}
