package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.ImageUploadRequest;
import com.ego.raw_ego.catalog.dto.request.ReorderImagesRequest;
import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Contract for variant-specific image management.
 *
 * <p>Variant images represent a specific colorway. The frontend replaces the main image
 * gallery when a color swatch is selected. Size selection does NOT change images.
 *
 * <p><b>Primary image rules (LOCKED):</b>
 * <ul>
 *   <li>Each variant must have exactly ONE image with {@code is_primary = true}.</li>
 *   <li>Setting a new primary automatically clears the old one in the same transaction.</li>
 *   <li>The first image uploaded to a variant is automatically promoted to primary.</li>
 * </ul>
 *
 * <p><b>Transaction pattern:</b> Cloudinary HTTP call outside {@code @Transactional};
 * DB operations inside.
 */
public interface VariantImageService {

    /** Returns all images for a variant ordered by display position. */
    List<ImageResponse> getVariantImages(Long variantId);

    /**
     * Uploads an image for a specific variant to Cloudinary and persists the record.
     * If this is the first image, it is automatically set as primary.
     */
    ImageResponse uploadAndSaveVariantImage(Long productId, Long variantId,
                                            MultipartFile file, ImageUploadRequest request);

    /**
     * Persists variant image metadata after a successful Cloudinary upload.
     * Auto-promotes to primary if no images exist yet for this variant.
     */
    ImageResponse persistVariantImage(Long variantId, String publicId,
                                      String secureUrl, ImageUploadRequest request);

    /**
     * Sets a specific image as the primary (hero) image for a variant.
     * Atomically clears old primary and sets the new one.
     */
    ImageResponse setPrimaryVariantImage(Long variantId, Long imageId, Long productId);

    /**
     * Deletes a variant image from DB (primary step) then from Cloudinary (best-effort).
     * If the deleted image was primary, auto-promotes the next image.
     */
    void deleteVariantImage(Long variantId, Long imageId);

    /**
     * Transactional step: validates ownership and deletes the DB record.
     *
     * @return the Cloudinary public_id for subsequent CDN cleanup
     */
    String deleteVariantImageFromDb(Long variantId, Long imageId);

    /**
     * Bulk-updates display order for all images of a variant.
     */
    List<ImageResponse> reorderVariantImages(Long variantId, ReorderImagesRequest request);
}
