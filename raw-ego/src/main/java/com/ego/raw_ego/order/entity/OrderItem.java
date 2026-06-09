package com.ego.raw_ego.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * JPA entity mapping to the {@code order_items} table.
 *
 * <p>Represents a single line item within an order. Every price and product
 * data field is a <b>snapshot</b> captured at checkout time — these values
 * are immutable once persisted and must never be updated retroactively.
 *
 * <p>Key snapshot fields:
 * <ul>
 *   <li>{@code unitPriceSnapshot} — the variant price at the moment of purchase.</li>
 *   <li>{@code productNameSnapshot} — guards against product renames in catalog.</li>
 *   <li>{@code skuSnapshot} — guards against SKU regeneration or variant archival.</li>
 *   <li>{@code variantLabelSnapshot} — guards against attribute value renames.</li>
 *   <li>{@code primaryImageUrlSnapshot} — guards against Cloudinary asset deletion.</li>
 * </ul>
 *
 * <p>{@code variantId} is retained as a raw Long FK (not an entity reference) to
 * avoid cross-module coupling between the order and catalog packages.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent order. LAZY — never accessed without the parent context.
     * Set via {@link Order#addItem(OrderItem)}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Raw FK to {@code product_variants.id}.
     * Not an entity reference — avoids cross-module Hibernate coupling.
     * Used for analytical JOIN queries only (not for live data fetching).
     */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    // ── Immutable snapshots (captured at checkout time) ───────────────────────

    /** SKU at time of purchase. Example: "EGO-MEN-0001-BLK-M". */
    @Column(name = "sku_snapshot", nullable = false, length = 100)
    private String skuSnapshot;

    /** Product name at time of purchase. Example: "Oversized Acid-Wash Tee". */
    @Column(name = "product_name_snapshot", nullable = false, length = 255)
    private String productNameSnapshot;

    /**
     * Human-readable variant label at time of purchase.
     * Example: "Black / M". Null if the variant has no attributes.
     */
    @Column(name = "variant_label_snapshot", length = 255)
    private String variantLabelSnapshot;

    /**
     * CDN URL of the variant's primary image at time of purchase.
     * Preserved here because the Cloudinary asset may be deleted later.
     * Null if no image existed at checkout time.
     */
    @Column(name = "primary_image_url_snapshot", columnDefinition = "TEXT")
    private String primaryImageUrlSnapshot;

    /**
     * Variant price at time of purchase. IMMUTABLE.
     * Must never be updated — all price corrections go through the Phase 10 refund flow.
     */
    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    /** Number of units purchased. */
    @Column(nullable = false)
    private int quantity;

    /**
     * Pre-computed: {@code unitPriceSnapshot × quantity}.
     * Stored redundantly for query performance — avoids multiplication at read time.
     */
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
}
