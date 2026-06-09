package com.ego.raw_ego.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A gallery-level product image — lifestyle shots, detail shots not tied to a specific variant.
 *
 * <p>Stored in Cloudinary at: {@code ego/{env}/products/{productId}/gallery/}
 *
 * <p>Only the {@code cloudinaryPublicId} is persisted — never a transformed URL.
 * The frontend generates transformed URLs via Cloudinary's URL builder at render time.
 *
 * <p>Contrast with {@link VariantImage} which is tied to a specific color variant.
 */
@Entity
@Table(
    name = "product_images",
    indexes = {
        @Index(name = "idx_product_image_product", columnList = "product_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Cloudinary public_id — the stable identifier used to construct transformed URLs.
     * Example: "ego/prod/products/1/gallery/lifestyle_shot_abc123"
     * Never change this after upload — Cloudinary caches by public_id.
     */
    @Column(name = "cloudinary_public_id", nullable = false, length = 300)
    private String cloudinaryPublicId;

    /**
     * Full Cloudinary delivery URL (original, no transformations).
     * Stored as a convenience for direct display and og:image tags.
     */
    @Column(nullable = false, length = 500)
    private String url;

    /** Accessibility alt text for screen readers and SEO. */
    @Column(name = "alt_text", length = 255)
    private String altText;

    /** Controls display order. Lower = shown first. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
