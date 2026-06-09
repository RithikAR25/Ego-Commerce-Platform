package com.ego.raw_ego.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Per-variant inventory record — exactly one row per {@link ProductVariant}.
 *
 * <p><b>Inventory model (LOCKED):</b>
 * Inventory is tracked per variant, never at the product level.
 * Black/M and Black/L are separate inventory rows with separate quantities.
 *
 * <p><b>Reservation lifecycle:</b>
 * <pre>
 *   ADD TO CART       → quantity_reserved += 1
 *   REMOVE FROM CART  → quantity_reserved -= 1
 *   PLACE ORDER       → quantity_available -= 1, quantity_reserved -= 1
 *   CANCEL ORDER      → quantity_available += 1
 *   RESERVE EXPIRY    → quantity_reserved -= 1  (Redis TTL cleanup — Phase 5)
 * </pre>
 *
 * <p><b>Optimistic locking:</b> The {@code version} field prevents overselling
 * on concurrent add-to-cart requests (Phase 5 — Redis cart implementation).
 *
 * <p>DB CHECK constraints prevent negative quantities — these are also enforced
 * at the service layer for early error feedback.
 */
@Entity
@Table(
    name = "inventory_records",
    indexes = {
        @Index(name = "uq_inventory_variant", columnList = "variant_id", unique = true),
        @Index(name = "idx_inventory_qty",    columnList = "quantity_available")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1:1 relationship — exactly one inventory record per variant. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private ProductVariant variant;

    /**
     * Units available for purchase.
     * DB constraint: CHECK (quantity_available >= 0).
     * Service also validates before decrement.
     */
    @Column(name = "quantity_available", nullable = false)
    @Builder.Default
    private Integer quantityAvailable = 0;

    /**
     * Units currently held in active carts (not yet ordered).
     * Incremented on add-to-cart, decremented on remove/order/expiry.
     * DB constraint: CHECK (quantity_reserved >= 0).
     */
    @Column(name = "quantity_reserved", nullable = false)
    @Builder.Default
    private Integer quantityReserved = 0;

    /**
     * When quantity_available <= this threshold, show "Low Stock" badge on frontend.
     * Also used to trigger admin low-stock alerts (Phase 8 — SendGrid).
     */
    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 5;

    /**
     * JPA optimistic lock version — prevents concurrent requests from overselling.
     * On version mismatch, JPA throws OptimisticLockException which the service
     * maps to a user-friendly "item sold out" message.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Computed helpers ─────────────────────────────────────────────────────

    /**
     * Stock status label for the storefront — never exposes raw numbers.
     *
     * @return "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK"
     */
    public StockStatus getStockStatus() {
        if (quantityAvailable <= 0) return StockStatus.OUT_OF_STOCK;
        if (quantityAvailable <= lowStockThreshold) return StockStatus.LOW_STOCK;
        return StockStatus.IN_STOCK;
    }

    public enum StockStatus {
        IN_STOCK, LOW_STOCK, OUT_OF_STOCK
    }
}
