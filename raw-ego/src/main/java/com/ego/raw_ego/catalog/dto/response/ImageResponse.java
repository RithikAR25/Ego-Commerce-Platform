package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.ProductImage;
import com.ego.raw_ego.catalog.entity.VariantImage;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Shared image response — used for both gallery images and variant images.
 *
 * <p>Contains the raw Cloudinary {@code public_id} plus pre-computed transformation
 * URLs for each standard display size. The frontend should always use the
 * {@code transformations} URLs — never construct Cloudinary URLs client-side.
 *
 * <p>{@code transformations} is populated by the image service layer when returning
 * upload/fetch responses. It may be null when returned as part of a nested product
 * response (e.g. PDP) to keep payloads lean — the frontend can use the raw {@code url}
 * in that case or request image-specific endpoints for full transformation sets.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageResponse {

    private Long id;
    private String url;
    private String cloudinaryPublicId;
    private String altText;
    private boolean primary;       // always false for gallery images
    private Integer displayOrder;

    /**
     * Pre-computed CDN transformation URLs. Populated on upload and image-specific
     * GET endpoints. Null when embedded inside product/variant responses for brevity.
     */
    private Transformations transformations;

    /**
     * Standard Cloudinary transformation URL set.
     *
     * <p>All URLs include {@code f_auto,q_auto} for automatic format (WebP/AVIF)
     * and quality negotiation. Sizes are optimized for each display context:
     * <ul>
     *   <li>{@code thumbnail} — 200×250, cart lines and order summaries</li>
     *   <li>{@code card}      — 400×500, product listing grid cards</li>
     *   <li>{@code detail}    — 800×1000, PDP main image slot</li>
     *   <li>{@code zoom}      — 1600×2000, PDP lightbox zoom overlay</li>
     * </ul>
     */
    @Data
    @Builder
    public static class Transformations {
        private String thumbnail;
        private String card;
        private String detail;
        private String zoom;
    }

    public static ImageResponse fromProductImage(ProductImage image) {
        return ImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .cloudinaryPublicId(image.getCloudinaryPublicId())
                .altText(image.getAltText())
                .primary(false)
                .displayOrder(image.getDisplayOrder())
                .build();
    }

    public static ImageResponse fromProductImage(ProductImage image, Transformations transformations) {
        return ImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .cloudinaryPublicId(image.getCloudinaryPublicId())
                .altText(image.getAltText())
                .primary(false)
                .displayOrder(image.getDisplayOrder())
                .transformations(transformations)
                .build();
    }

    public static ImageResponse fromVariantImage(VariantImage image) {
        return ImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .cloudinaryPublicId(image.getCloudinaryPublicId())
                .altText(image.getAltText())
                .primary(image.isPrimary())
                .displayOrder(image.getDisplayOrder())
                .build();
    }

    public static ImageResponse fromVariantImage(VariantImage image, Transformations transformations) {
        return ImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .cloudinaryPublicId(image.getCloudinaryPublicId())
                .altText(image.getAltText())
                .primary(image.isPrimary())
                .displayOrder(image.getDisplayOrder())
                .transformations(transformations)
                .build();
    }
}
