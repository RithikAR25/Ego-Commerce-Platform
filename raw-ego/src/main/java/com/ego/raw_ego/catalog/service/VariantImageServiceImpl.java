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
 * Implementation of {@link VariantImageService} — variant-specific image management.
 *
 * <p><b>Primary image rules (LOCKED):</b>
 * <ul>
 *   <li>Each variant must have exactly ONE image with {@code is_primary = true}.</li>
 *   <li>Setting a new primary automatically clears the old one in the same transaction.</li>
 *   <li>The first image uploaded to a variant is automatically promoted to primary.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VariantImageServiceImpl implements VariantImageService {

    private final VariantImageRepository   variantImageRepository;
    private final ProductVariantRepository variantRepository;
    private final CloudinaryService        cloudinaryService;

    @Override
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

    @Override
    public ImageResponse uploadAndSaveVariantImage(Long productId, Long variantId,
                                                   MultipartFile file, ImageUploadRequest request) {
        Map<String, Object> uploadResult = cloudinaryService.uploadVariantImage(file, productId, variantId);
        String publicId  = (String) uploadResult.get("public_id");
        String secureUrl = (String) uploadResult.get("secure_url");
        return persistVariantImage(variantId, publicId, secureUrl, request);
    }

    @Override
    @Transactional
    public ImageResponse persistVariantImage(Long variantId, String publicId,
                                             String secureUrl, ImageUploadRequest request) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: id=" + variantId));

        boolean noExistingImages = variantImageRepository
                .findByVariantIdOrderByDisplayOrderAsc(variantId).isEmpty();
        boolean isPrimary = noExistingImages;

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

    @Override
    @Transactional
    public ImageResponse setPrimaryVariantImage(Long variantId, Long imageId, Long productId) {
        VariantImage image = variantImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant image not found: id=" + imageId));

        if (!image.getVariant().getId().equals(variantId)) {
            throw new ResourceNotFoundException(
                    "Image id=" + imageId + " does not belong to variant id=" + variantId);
        }

        variantImageRepository.clearPrimaryFlagForVariant(variantId);
        image.setPrimary(true);
        image = variantImageRepository.save(image);

        log.info("Primary variant image set: imageId={} variantId={}", imageId, variantId);
        return ImageResponse.fromVariantImage(
                image,
                cloudinaryService.buildTransformationUrls(image.getCloudinaryPublicId())
        );
    }

    @Override
    public void deleteVariantImage(Long variantId, Long imageId) {
        String publicId = deleteVariantImageFromDb(variantId, imageId);
        cloudinaryService.deleteImage(publicId);
    }

    @Override
    @Transactional
    public String deleteVariantImageFromDb(Long variantId, Long imageId) {
        VariantImage image = variantImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant image not found: id=" + imageId));

        if (!image.getVariant().getId().equals(variantId)) {
            throw new ResourceNotFoundException(
                    "Image id=" + imageId + " does not belong to variant id=" + variantId);
        }

        boolean wasPrimary = image.isPrimary();
        String  publicId   = image.getCloudinaryPublicId();

        variantImageRepository.delete(image);
        log.info("Variant image deleted from DB: id={} variantId={} wasPrimary={}", imageId, variantId, wasPrimary);

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

    @Override
    @Transactional
    public List<ImageResponse> reorderVariantImages(Long variantId, ReorderImagesRequest request) {
        ensureVariantExists(variantId);

        List<VariantImage> existing =
                variantImageRepository.findByVariantIdOrderByDisplayOrderAsc(variantId);
        var existingIds = existing.stream().map(VariantImage::getId).collect(Collectors.toSet());

        for (var entry : request.getOrder()) {
            if (!existingIds.contains(entry.getImageId())) {
                throw new IllegalArgumentException(
                        "Image id=" + entry.getImageId() + " does not belong to variant id=" + variantId);
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
