package com.ego.raw_ego.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A specific, purchasable combination of attribute values for a product.
 *
 * <p>Example: "Oversized Acid-Wash Tee / Black / M" is one ProductVariant row.
 *
 * <p><b>SKU contract (LOCKED):</b>
 * <pre>EGO-{CATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE}</pre>
 * Example: {@code EGO-TEE-0001-BLK-M}
 * <ul>
 *   <li>Generated server-side in {@link com.ego.raw_ego.catalog.service.ProductVariantService}</li>
 *   <li>Never null, never changed after creation</li>
 *   <li>Indexed UNIQUE in DB</li>
 * </ul>
 *
 * <p><b>Price model (LOCKED):</b>
 * <ul>
 *   <li>{@code price}           — final selling price shown to customer</li>
 *   <li>{@code compareAtPrice}  — crossed-out original price for discount display (nullable)</li>
 *   <li>{@code costPrice}       — internal COGS, NEVER exposed in public API responses</li>
 * </ul>
 *
 * <p>Cart, orders, and inventory all reference this entity — never {@link Product} directly.
 */
@Entity
@Table(
    name = "product_variants",
    indexes = {
        @Index(name = "uq_variant_sku",     columnList = "sku",        unique = true),
        @Index(name = "idx_variant_product", columnList = "product_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Immutable SKU. Format: EGO-{CAT}-{PROD}-{COLOR}-{SIZE}.
     * Set once on creation by ProductVariantService. Never updated.
     */
    @Column(nullable = false, length = 50, unique = true)
    private String sku;

    /**
     * Final selling price — the amount charged at checkout.
     * This is the ONLY price used in order calculation.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Optional original price for discount display in the UI.
     * When set: discount% = round((compareAtPrice - price) / compareAtPrice * 100)
     * Must be > price when non-null (enforced at service layer).
     */
    @Column(name = "compare_at_price", precision = 10, scale = 2)
    private BigDecimal compareAtPrice;

    /**
     * Internal cost / COGS. Used for margin reporting in admin dashboard (Phase 11).
     * NEVER included in any public API response — filtered at the DTO mapping layer.
     */
    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    /** Weight in grams — used for shipping rate calculation in Phase 6. */
    @Column(name = "weight_grams")
    private Integer weightGrams;

    /** When false, variant is hidden from storefront (e.g. discontinued color). */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * The attribute values (Color=Black, Size=M) that define this variant.
     * Resolved via the join table {@code variant_attribute_values}.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "variant_attribute_values",
        joinColumns        = @JoinColumn(name = "variant_id"),
        inverseJoinColumns = @JoinColumn(name = "attribute_value_id")
    )
    @Builder.Default
    private List<AttributeValue> attributeValues = new ArrayList<>();

    /**
     * Inventory record for this variant (1:1).
     * Wired BEFORE variantRepository.save() so CascadeType.ALL handles the INSERT.
     * orphanRemoval deletes inventory when the variant is deleted.
     */
    @OneToOne(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private InventoryRecord inventoryRecord;

    /**
     * Variant-specific images (e.g. all Black colorway photos).
     * Frontend swaps main image gallery when user selects a different color swatch.
     */
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<VariantImage> images = new ArrayList<>();

    /** Optimistic lock — prevents concurrent price/stock updates from conflicting. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Computed helpers ─────────────────────────────────────────────────────

    /**
     * Discount percentage for display — calculated dynamically, never stored.
     * Returns null if no compareAtPrice is set or compareAtPrice <= price.
     */
    public Integer getDiscountPercent() {
        if (compareAtPrice == null || compareAtPrice.compareTo(price) <= 0) return null;
        double discount = (compareAtPrice.subtract(price).doubleValue() / compareAtPrice.doubleValue()) * 100;
        return (int) Math.round(discount);
    }
}
