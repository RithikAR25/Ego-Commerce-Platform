package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.ImageUploadRequest;
import com.ego.raw_ego.catalog.dto.request.ReorderImagesRequest;
import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import com.ego.raw_ego.catalog.entity.Product;
import com.ego.raw_ego.catalog.entity.ProductImage;
import com.ego.raw_ego.catalog.repository.ProductImageRepository;
import com.ego.raw_ego.catalog.repository.ProductRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic for product-level gallery image management.
 *
 * <p><b>Orchestration pattern:</b> This service orchestrates the two-step upload flow:
 * <ol>
 *   <li>Call {@link CloudinaryService#uploadProductGalleryImage} — external HTTP, NO transaction.</li>
 *   <li>Call {@link #persistProductImage} — database write, wrapped in {@code @Transactional}.</li>
 * </ol>
 * The two steps are deliberately separated to honour the architecture rule:
 * "Do not wrap slow external HTTP calls inside database transactional contexts."
 *
 * <p>The controller coordinates the two calls at the endpoint level.
 *
 * <p><b>Deletion pattern:</b>
 * <ol>
 *   <li>Delete DB record first (inside transaction).</li>
 *   <li>Call Cloudinary delete after (outside transaction, best-effort).</li>
 * </ol>
 * If Cloudinary delete fails, an orphaned asset remains — acceptable hygiene risk.
 * If DB delete fails, the transaction rolls back — Cloudinary asset is still valid, no corruption.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository      productRepository;
    private final CloudinaryService      cloudinaryService;

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns all gallery images for a product, ordered by display position.
     * Each image response includes full transformation URLs.
     */
    @Transactional(readOnly = true)
    public List<ImageResponse> getProductImages(Long productId) {
        ensureProductExists(productId);

        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(img -> ImageResponse.fromProductImage(
                        img,
                        cloudinaryService.buildTransformationUrls(img.getCloudinaryPublicId())
                ))
                .collect(Collectors.toList());
    }

    // ── Upload (2-step: Cloudinary → DB) ──────────────────────────────────────

    /**
     * Uploads an image to Cloudinary and registers it in the database.
     *
     * <p><b>IMPORTANT:</b> This method performs BOTH the Cloudinary upload AND the DB save.
     * The Cloudinary call is NOT inside the @Transactional context — Hibernate's session
     * is not held open during the HTTP call to Cloudinary. The DB write is a separate
     * transactional operation that runs after the upload completes.
     *
     * <p>If Cloudinary succeeds but the DB save fails, the Cloudinary asset is orphaned.
     * This is a known acceptable trade-off. A cleanup job (future Phase) can detect and
     * remove orphaned assets by comparing Cloudinary folder listings to DB records.
     *
     * @param productId the product to attach this image to
     * @param file      raw image multipart file
     * @param request   optional metadata (altText, displayOrder)
     * @return the created image record with transformation URLs
     */
    public ImageResponse uploadAndSaveProductImage(Long productId, MultipartFile file,
                                                   ImageUploadRequest request) {
        // Step 1: Upload to Cloudinary (external HTTP — intentionally outside @Transactional)
        Map<String, Object> uploadResult = cloudinaryService.uploadProductGalleryImage(file, productId);
        String publicId  = (String) uploadResult.get("public_id");
        String secureUrl = (String) uploadResult.get("secure_url");

        // Step 2: Persist to DB in its own transaction
        return persistProductImage(productId, publicId, secureUrl, request);
    }

    /**
     * Persists the image metadata to the database after a successful Cloudinary upload.
     * Called as a separate step from the upload so that the DB transaction is not held
     * open during the Cloudinary HTTP call.
     */
    @Transactional
    public ImageResponse persistProductImage(Long productId, String publicId,
                                             String secureUrl, ImageUploadRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: id=" + productId));

        // Resolve display order — append to end if not specified
        int displayOrder = resolveDisplayOrder(request, productId);
        String altText   = request != null && request.getAltText() != null
                ? request.getAltText().trim() : "";

        ProductImage image = ProductImage.builder()
                .product(product)
                .cloudinaryPublicId(publicId)
                .url(secureUrl)
                .altText(altText)
                .displayOrder(displayOrder)
                .build();

        image = productImageRepository.save(image);
        log.info("Product gallery image saved: id={} productId={} publicId={} displayOrder={}",
                image.getId(), productId, publicId, displayOrder);

        return ImageResponse.fromProductImage(
                image,
                cloudinaryService.buildTransformationUrls(publicId)
        );
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a product gallery image from the database and then from Cloudinary.
     *
     * <p>Deletion order:
     * <ol>
     *   <li>Verify the image exists and belongs to the specified product (anti-IDOR).</li>
     *   <li>Delete the DB record inside a transaction.</li>
     *   <li>Delete from Cloudinary outside the transaction (best-effort, non-fatal on failure).</li>
     * </ol>
     *
     * @param productId the product this image must belong to (ownership check)
     * @param imageId   the image database ID to delete
     */
    public void deleteProductImage(Long productId, Long imageId) {
        // Step 1: Resolve public_id inside transaction, then delete DB record
        String publicId = deleteProductImageFromDb(productId, imageId);

        // Step 2: Delete from Cloudinary (outside transaction — best-effort)
        cloudinaryService.deleteImage(publicId);
    }

    /**
     * Transactional step: validates ownership and deletes the DB record.
     *
     * @return the Cloudinary public_id for subsequent CDN cleanup
     */
    @Transactional
    public String deleteProductImageFromDb(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product image not found: id=" + imageId));

        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException(
                    "Image id=" + imageId + " does not belong to product id=" + productId);
        }

        String publicId = image.getCloudinaryPublicId();
        productImageRepository.delete(image);
        log.info("Product gallery image deleted from DB: id={} productId={}", imageId, productId);

        return publicId;
    }

    // ── Reorder ───────────────────────────────────────────────────────────────

    /**
     * Bulk-updates the display order for product gallery images.
     *
     * <p>The admin sends the complete new ordering from the UI. This method applies
     * all order updates atomically in a single transaction using bulk JPQL updates.
     *
     * <p>Validation: all imageIds in the request must belong to the specified product.
     * Any foreign image ID causes an {@link IllegalArgumentException}.
     *
     * @param productId     the product whose images are being reordered
     * @param reorderRequest the new complete ordering
     * @return the updated image list in new display order
     */
    @Transactional
    public List<ImageResponse> reorderProductImages(Long productId, ReorderImagesRequest reorderRequest) {
        ensureProductExists(productId);

        // Validate all image IDs belong to this product
        List<ProductImage> existing = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(productId);
        var existingIds = existing.stream().map(ProductImage::getId).collect(Collectors.toSet());

        for (var entry : reorderRequest.getOrder()) {
            if (!existingIds.contains(entry.getImageId())) {
                throw new IllegalArgumentException(
                        "Image id=" + entry.getImageId() +
                        " does not belong to product id=" + productId);
            }
        }

        // Apply order updates
        for (var entry : reorderRequest.getOrder()) {
            productImageRepository.updateDisplayOrder(entry.getImageId(), entry.getDisplayOrder());
        }

        log.info("Reordered {} images for product={}", reorderRequest.getOrder().size(), productId);

        // Return updated list
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(img -> ImageResponse.fromProductImage(
                        img,
                        cloudinaryService.buildTransformationUrls(img.getCloudinaryPublicId())
                ))
                .collect(Collectors.toList());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: id=" + productId);
        }
    }

    /**
     * Resolves the display order for a new image.
     * If explicitly provided in the request, uses that value.
     * Otherwise, appends after the current last image.
     */
    private int resolveDisplayOrder(ImageUploadRequest request, Long productId) {
        if (request != null && request.getDisplayOrder() != null) {
            return request.getDisplayOrder();
        }
        // Auto-assign: count existing + position at end
        return (int) productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId).size();
    }
}
