package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.ImageUploadRequest;
import com.ego.raw_ego.catalog.dto.request.ReorderImagesRequest;
import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Contract for product-level gallery image management.
 *
 * <p><b>Orchestration pattern:</b>
 * <ol>
 *   <li>Call {@link CloudinaryService#uploadProductGalleryImage} — external HTTP, NO transaction.</li>
 *   <li>Call {@link #persistProductImage} — database write, wrapped in {@code @Transactional}.</li>
 * </ol>
 *
 * <p><b>Deletion pattern:</b>
 * <ol>
 *   <li>Delete DB record first (inside transaction).</li>
 *   <li>Call Cloudinary delete after (outside transaction, best-effort).</li>
 * </ol>
 */
public interface ProductImageService {

    /** Returns all gallery images for a product, ordered by display position. */
    List<ImageResponse> getProductImages(Long productId);

    /**
     * Uploads an image to Cloudinary and registers it in the database.
     * The Cloudinary call is NOT inside a @Transactional context.
     */
    ImageResponse uploadAndSaveProductImage(Long productId, MultipartFile file, ImageUploadRequest request);

    /**
     * Persists image metadata to the database after a successful Cloudinary upload.
     * Called as a separate transactional step from the upload.
     */
    ImageResponse persistProductImage(Long productId, String publicId, String secureUrl, ImageUploadRequest request);

    /**
     * Deletes a product gallery image from the database and then from Cloudinary (best-effort).
     */
    void deleteProductImage(Long productId, Long imageId);

    /**
     * Transactional step: validates ownership and deletes the DB record.
     *
     * @return the Cloudinary public_id for subsequent CDN cleanup
     */
    String deleteProductImageFromDb(Long productId, Long imageId);

    /**
     * Bulk-updates the display order for product gallery images.
     */
    List<ImageResponse> reorderProductImages(Long productId, ReorderImagesRequest reorderRequest);
}
