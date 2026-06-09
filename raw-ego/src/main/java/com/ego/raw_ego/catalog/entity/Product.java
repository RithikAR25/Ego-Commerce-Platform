package com.ego.raw_ego.catalog.entity;

import com.ego.raw_ego.catalog.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a base product concept in the EGO catalog.
 *
 * <p>A Product is the "concept" — e.g. "Oversized Acid-Wash Tee".
 * Purchasable units are {@link ProductVariant} rows (e.g. "Black / M", "White / XL").
 * Cart, orders, and inventory all reference variants — never products directly.
 *
 * <p><b>Key fields:</b>
 * <ul>
 *   <li>{@code productCode} — zero-padded 4-digit sequence (e.g. "0001") used in SKU generation.</li>
 *   <li>{@code slug}        — unique SEO-friendly URL identifier (e.g. "oversized-acid-wash-tee").</li>
 *   <li>{@code status}      — lifecycle state. See {@link ProductStatus} for transition rules.</li>
 *   <li>{@code tags}        — JSON array of strings for Elasticsearch indexing.</li>
 *   <li>{@code isDeleted}   — soft-delete flag. When {@code true}, the product is excluded from
 *       ALL queries via {@code @SQLRestriction}. Admins soft-delete by setting this flag;
 *       hard-delete is reserved for products with no order history.</li>
 * </ul>
 *
 * <p>{@code category} must always be a SUBCATEGORY (depth=1), never a root category.
 * This is enforced at the service layer.
 */
@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "uq_product_slug",     columnList = "slug",         unique = true),
        @Index(name = "uq_product_code",     columnList = "product_code", unique = true),
        @Index(name = "idx_product_category",columnList = "category_id"),
        @Index(name = "idx_product_status",  columnList = "status"),
        @Index(name = "idx_product_deleted", columnList = "is_deleted")
    }
)
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Must be a SUBCATEGORY (depth=1). Enforced in ProductService.
     * Used to derive the category code for SKU generation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Full display name, e.g. "Oversized Acid-Wash Tee". */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Zero-padded 4-digit code, e.g. "0001".
     * Used as the {PRODUCT_CODE} segment in SKU: EGO-TEE-{0001}-BLK-M.
     * Never changes after creation — part of the immutable SKU.
     */
    @Column(name = "product_code", nullable = false, length = 10, unique = true)
    private String productCode;

    /**
     * SEO-friendly URL slug, e.g. "oversized-acid-wash-tee".
     * Unique. Used in storefront routes: /products/{slug}.
     * Generated server-side by SlugUtils from the product name.
     */
    @Column(nullable = false, length = 300, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** e.g. "100% Cotton", "80% Cotton, 20% Polyester" */
    @Column(length = 255)
    private String material;

    @Column(name = "care_instructions", columnDefinition = "TEXT")
    private String careInstructions;

    /**
     * Lifecycle status. Defaults to DRAFT on creation.
     * OUT_OF_STOCK is managed automatically by InventoryService.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20,
            columnDefinition = "ENUM('DRAFT','ACTIVE','OUT_OF_STOCK','ARCHIVED') DEFAULT 'DRAFT'")
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    /**
     * JSON array of string tags for search indexing, e.g. ["streetwear","oversized"].
     * Stored as JSON in MySQL, synced to Elasticsearch.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> tags;

    /**
     * Soft-delete flag.
     *
     * <p>When {@code true}, the product is excluded from ALL JPA queries via the
     * {@code @SQLRestriction("is_deleted = false")} class annotation.
     * This is the preferred way to remove a product from the storefront without
     * losing order history or audit trails.
     *
     * <p><b>Transition rules:</b>
     * <ul>
     *   <li>Admin soft-deletes via {@code DELETE /api/v1/admin/products/{id}}: sets
     *       {@code isDeleted=true} and publishes an ES DELETE outbox entry.</li>
     *   <li>Hard-delete ({@link com.ego.raw_ego.catalog.service.ProductService#hardDeleteProduct})
     *       is still available for products with zero order history (e.g. test data cleanup).</li>
     *   <li>A soft-deleted product can be restored by setting {@code isDeleted=false} via admin.</li>
     * </ul>
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /**
     * Variants of this product (different color/size combinations).
     * Orphan removal ensures deleting a product cascades to all variants.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    /**
     * Gallery-level images — lifestyle shots, detail shots not tied to a specific variant.
     * Variant-specific images are in {@link VariantImage}.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    /**
     * Attribute types defined for this product (e.g. "Color", "Size").
     * Values under each type form the variant matrix.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AttributeType> attributeTypes = new ArrayList<>();

    /** Optimistic lock — prevents concurrent admin edits from silently overwriting each other. */
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
}
