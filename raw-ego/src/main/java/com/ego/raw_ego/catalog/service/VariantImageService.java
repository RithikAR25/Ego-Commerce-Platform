package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.ImageUploadRequest;
import com.ego.raw_ego.catalog.dto.request.ReorderImagesRequest;
import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import com.ego.raw_ego.catalog.entity.ProductVariant;
import com.ego.raw_ego.catalog.entity.VariantImage;
import com.ego.raw_ego.catalog.repository.ProductVariantRepository;
import com.ego.raw_ego.catalog.repository.VariantImageRepository;
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
 * Business logic for variant-specific image management.
 *
 * <p>Variant images represent a specific colorway (e.g., all Black hoodie photos).
 * The frontend replaces the main image gallery when a color swatch is selected —
 * Size selection does NOT change images (LOCKED behavior per {@link com.ego.raw_ego.catalog.entity.VariantImage}).
 *
 * <p><b>Primary image rules (LOCKED):</b>
 * <ul>
 *   <li>Each variant must have exactly ONE image with {@code is_primary = true}.</li>
 *   <li>Setting a new primary automatically clears the old one in the same transaction.</li>
 *   <li>The first image uploaded to a variant is automatically promoted to primary.</li>
 *   <li>The primary image is displayed in product listing cards and the PDP main slot
 *       when that variant's color is selected.</li>
 * </ul>
 *
 * <p><b>Transaction pattern:</b> Same two-step orchestration as {@link ProductImageService}.
 * Cloudinary HTTP call outside {@code @Transactional}; DB operations inside.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VariantImageService {

    private final VariantImageRepository   variantImageRepository;
    private final ProductVariantRepository variantRepository;
    private final CloudinaryService        cloudinaryService;

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns all images for a variant ordered by display position.
     * The primary image (hero) is always first by convention (displayOrder=0).
     */
    @Transactional(readOnly = true)
    public List<ImageResponse> getVariantImages(Long variantId) {
        ensureVariantExists(variantId);

        return variantImageRepository.findByVariantIdOrderByDisplayOrderAsc(variantId)
                .stream()
                .map(img -> ImageResponse.fromVariantImage(
                        img,
                        cloudinaryService.buildTransformationUrls(img.getCloudinaryPublicId())
                ))
                .collect(Collectors.toList());
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Uploads an image for a specific variant to Cloudinary and persists the record.
     *
     * <p>If this is the first image for the variant, it is automatically set as primary.
     * Subsequent uploads default to non-primary unless explicitly set via
     * {@link #setPrimaryVariantImage(Long, Long, Long)}.
     *
     * @param productId the parent product ID (used in Cloudinary folder path)
     * @param variantId the variant to attach this image to
     * @param file      the raw image multipart file
     * @param request   optional metadata (altText, displayOrder)
     * @return the created image record with transformation URLs
     */
    public ImageResponse uploadAndSaveVariantImage(Long productId, Long variantId,
                                                   MultipartFile file, ImageUploadRequest request) {
        // Step 1: Upload to Cloudinary (external HTTP — intentionally outside @Transactional)
        Map<String, Object> uploadResult = cloudinaryService.uploadVariantImage(file, productId, variantId);
        String publicId  = (String) uploadResult.get("public_id");
        String secureUrl = (String) uploadResult.get("secure_url");

        // Step 2: Persist to DB in its own transaction
        return persistVariantImage(variantId, publicId, secureUrl, request);
    }

    /**
     * Persists variant image metadata after a successful Cloudinary upload.
     *
     * <p>Auto-primary logic: if no images exist for this variant yet,
     * {@code isPrimary} is set to {@code true} automatically.
     */
    @Transactional
    public ImageResponse persistVariantImage(Long variantId, String publicId,
                                             String secureUrl, ImageUploadRequest request) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Variant not found: id=" + variantId));

        // Auto-promote to primary if this is the first image for this variant
        boolean existingImages = variantImageRepository
                .findByVariantIdOrderByDisplayOrderAsc(variantId).isEmpty();
        boolean isPrimary = existingImages; // true only when no images exist yet

        int displayOrder = resolveVariantDisplayOrder(request, variantId);
        String altText   = request != null && request.getAltText() != null
                ? request.getAltText().trim() : "";

        VariantImage image = VariantImage.builder()
                .variant(variant)
                .cloudinaryPublicId(publicId)
                .url(secureUrl)
                .altText(altText)
                .primary(isPrimary)
                .displayOrder(displayOrder)
                .build();

        image = variantImageRepository.save(image);
        log.info("Variant image saved: id={} variantId={} publicId={} isPrimary={} displayOrder={}",
                image.getId(), variantId, publicId, isPrimary, displayOrder);

        return ImageResponse.fromVariantImage(
                image,
                cloudinaryService.buildTransformationUrls(publicId)
        );
    }

    // ── Primary image management ──────────────────────────────────────────────

    /**
     * Sets a specific image as the primary (hero) image for a variant.
     *
     * <p>This operation is atomic:
     * <ol>
     *   <li>Clear the existing primary flag for ALL images of this variant.</li>
     *   <li>Set the specified image's flag to {@code true}.</li>
     * </ol>
     * Both happen inside a single {@code @Transactional} boundary.
     *
     * <p>The image must belong to the specified variant (anti-IDOR validation).
     *
     * @param variantId the variant whose primary is being changed
     * @param imageId   the image to promote to primary
     * @return the updated image response with {@code primary = true}
     */
    @Transactional
    public ImageResponse setPrimaryVariantImage(Long variantId, Long imageId, Long productId) {
        VariantImage image = variantImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Variant image not found: id=" + imageId));

        // Ownership validation (anti-IDOR)
        if (!image.getVariant().getId().equals(variantId)) {
            throw new ResourceNotFoundException(
                    "Image id=" + imageId + " does not belong to variant id=" + variantId);
        }

        // Clear existing primary flag atomically
        variantImageRepository.clearPrimaryFlagForVariant(variantId);

        // Set new primary
        image.setPrimary(true);
        image = variantImageRepository.save(image);

        log.info("Primary variant image set: imageId={} variantId={}", imageId, variantId);

        return ImageResponse.fromVariantImage(
                image,
                cloudinaryService.buildTransformationUrls(image.getCloudinaryPublicId())
        );
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a variant image from DB (primary step) then from Cloudinary (best-effort).
     *
     * <p>If the deleted image was the primary, promotes the next image (lowest displayOrder)
     * to primary automatically — a variant should always have a primary if images remain.
     *
     * @param variantId the variant this image must belong to (ownership check)
     * @param imageId   the image ID to delete
     */
    public void deleteVariantImage(Long variantId, Long imageId) {
        // Step 1: DB delete inside transaction
        String publicId = deleteVariantImageFromDb(variantId, imageId);

        // Step 2: Cloudinary delete outside transaction (best-effort)
        cloudinaryService.deleteImage(publicId);
    }

    @Transactional
    public String deleteVariantImageFromDb(Long variantId, Long imageId) {
        VariantImage image = variantImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Variant image not found: id=" + imageId));

        if (!image.getVariant().getId().equals(variantId)) {
            throw new ResourceNotFoundException(
                    "Image id=" + imageId + " does not belong to variant id=" + variantId);
        }

        boolean wasPrimary = image.isPrimary();
        String  publicId   = image.getCloudinaryPublicId();

        variantImageRepository.delete(image);
        log.info("Variant image deleted from DB: id={} variantId={} wasPrimary={}",
                imageId, variantId, wasPrimary);

        // Auto-promote next image to primary if the deleted image was primary
        if (wasPrimary) {
            List<VariantImage> remaining =
                    variantImageRepository.findByVariantIdOrderByDisplayOrderAsc(variantId);
            if (!remaining.isEmpty()) {
                VariantImage newPrimary = remaining.get(0);
                newPrimary.setPrimary(true);
                variantImageRepository.save(newPrimary);
                log.info("Auto-promoted next image to primary: imageId={} variantId={}",
                        newPrimary.getId(), variantId);
            }
        }

        return publicId;
    }

    // ── Reorder ───────────────────────────────────────────────────────────────

    /**
     * Bulk-updates display order for all images of a variant.
     *
     * <p>Works identically to product image reorder but scoped to a specific variant.
     * All imageIds in the request must belong to the specified variant.
     */
    @Transactional
    public List<ImageResponse> reorderVariantImages(Long variantId, ReorderImagesRequest request) {
        ensureVariantExists(variantId);

        List<VariantImage> existing =
                variantImageRepository.findByVariantIdOrderByDisplayOrderAsc(variantId);
        var existingIds = existing.stream().map(VariantImage::getId).collect(Collectors.toSet());

        for (var entry : request.getOrder()) {
            if (!existingIds.contains(entry.getImageId())) {
                throw new IllegalArgumentException(
                        "Image id=" + entry.getImageId() +
                        " does not belong to variant id=" + variantId);
            }
        }

        for (var entry : request.getOrder()) {
            variantImageRepository.updateDisplayOrder(entry.getImageId(), entry.getDisplayOrder());
        }

        log.info("Reordered {} images for variant={}", request.getOrder().size(), variantId);

        return variantImageRepository.findByVariantIdOrderByDisplayOrderAsc(variantId)
                .stream()
                .map(img -> ImageResponse.fromVariantImage(
                        img,
                        cloudinaryService.buildTransformationUrls(img.getCloudinaryPublicId())
                ))
                .collect(Collectors.toList());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void ensureVariantExists(Long variantId) {
        if (!variantRepository.existsById(variantId)) {
            throw new ResourceNotFoundException("Variant not found: id=" + variantId);
        }
    }

    private int resolveVariantDisplayOrder(ImageUploadRequest request, Long variantId) {
        if (request != null && request.getDisplayOrder() != null) {
            return request.getDisplayOrder();
        }
        return variantImageRepository.findByVariantIdOrderByDisplayOrderAsc(variantId).size();
    }
}
