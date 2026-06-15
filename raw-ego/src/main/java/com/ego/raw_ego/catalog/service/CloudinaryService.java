package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Contract for Cloudinary infrastructure operations.
 *
 * <p><b>Responsibility boundary:</b> This service only handles Cloudinary I/O — uploading
 * and deleting files, and constructing CDN transformation URLs. No database interactions.
 *
 * <p><b>Transaction boundary:</b> Methods here must NEVER be called inside an active
 * {@code @Transactional} context. Cloudinary HTTP calls can take 1–5 seconds and would
 * hold the database connection pool for that entire duration.
 *
 * <p><b>Folder naming (LOCKED):</b>
 * <pre>
 *   Gallery images:  ego/{env}/products/{productId}/gallery/
 *   Variant images:  ego/{env}/products/{productId}/variants/{variantId}/
 * </pre>
 */
public interface CloudinaryService {

    /**
     * Uploads a product gallery image to Cloudinary.
     *
     * @throws com.ego.raw_ego.common.exception.ImageUploadException on validation failure or API error
     */
    Map<String, Object> uploadProductGalleryImage(MultipartFile file, Long productId);

    /**
     * Uploads a variant-specific image to Cloudinary.
     *
     * @throws com.ego.raw_ego.common.exception.ImageUploadException on validation failure or API error
     */
    Map<String, Object> uploadVariantImage(MultipartFile file, Long productId, Long variantId);

    /**
     * Deletes an image from Cloudinary by its public_id.
     * Non-fatal on failure — logs WARN but does not throw.
     */
    void deleteImage(String cloudinaryPublicId);

    /**
     * Constructs all four standard transformation URLs from a Cloudinary public_id.
     */
    ImageResponse.Transformations buildTransformationUrls(String cloudinaryPublicId);
}
