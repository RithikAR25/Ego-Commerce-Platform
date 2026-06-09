package com.ego.raw_ego.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A variant-specific image — e.g. all photos for the "Black" colorway of a hoodie.
 *
 * <p>Stored in Cloudinary at: {@code ego/{env}/products/{productId}/variants/{variantId}/}
 *
 * <p><b>Frontend behavior (LOCKED):</b>
 * When the user selects a color swatch, the frontend fetches the variant's images
 * and replaces the main gallery. Size selection does NOT change images.
 *
 * <p>{@code isPrimary = true} marks the hero image displayed as the default
 * for that variant in the main image slot and product listing cards.
 *
 * <p>Contrast with {@link ProductImage} which is not tied to a specific variant.
 */
@Entity
@Table(
    name = "variant_images",
    indexes = {
        @Index(name = "idx_variant_image_variant", columnList = "variant_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariantImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    /**
     * Cloudinary public_id — stable identifier for URL construction.
     * Example: "ego/prod/products/1/variants/7/black_front_hero"
     */
    @Column(name = "cloudinary_public_id", nullable = false, length = 300)
    private String cloudinaryPublicId;

    /** Full Cloudinary delivery URL (original). */
    @Column(nullable = false, length = 500)
    private String url;

    /** Accessibility alt text. */
    @Column(name = "alt_text", length = 255)
    private String altText;

    /**
     * When true, this image is the hero shown in:
     * - Product listing card (when this color is selected)
     * - Main image slot on product detail page
     * - Cart thumbnail
     * Each variant should have exactly one primary image.
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    /** Controls display order in the thumbnail strip. Lower = first. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
