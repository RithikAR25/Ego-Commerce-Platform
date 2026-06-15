package com.ego.raw_ego.catalog.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import com.ego.raw_ego.common.exception.ImageUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link CloudinaryService} — wraps the Cloudinary Java SDK.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.environment:dev}")
    private String environment;

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;

    private static final String TRANSFORM_THUMBNAIL = "w_200,h_250,c_fill,g_auto,q_auto,f_auto";
    private static final String TRANSFORM_CARD      = "w_400,h_500,c_fill,g_auto,q_auto,f_auto";
    private static final String TRANSFORM_DETAIL    = "w_800,h_1000,c_fill,g_auto,q_auto,f_auto";
    private static final String TRANSFORM_ZOOM      = "w_1600,h_2000,c_limit,q_auto,f_auto";

    @Override
    public Map<String, Object> uploadProductGalleryImage(MultipartFile file, Long productId) {
        validateFile(file);
        String folder = buildGalleryFolder(productId);
        log.info("Uploading gallery image for product={} to folder={}", productId, folder);
        return performUpload(file, folder);
    }

    @Override
    public Map<String, Object> uploadVariantImage(MultipartFile file, Long productId, Long variantId) {
        validateFile(file);
        String folder = buildVariantFolder(productId, variantId);
        log.info("Uploading variant image for variant={} product={} to folder={}", variantId, productId, folder);
        return performUpload(file, folder);
    }

    @Override
    public void deleteImage(String cloudinaryPublicId) {
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(cloudinaryPublicId, ObjectUtils.emptyMap());
            String resultStr = String.valueOf(result.get("result"));

            if ("ok".equals(resultStr)) {
                log.info("Cloudinary image deleted: publicId={}", cloudinaryPublicId);
            } else {
                log.warn("Cloudinary deletion returned unexpected result: publicId={} result={}",
                        cloudinaryPublicId, resultStr);
            }
        } catch (IOException e) {
            log.warn("Cloudinary deletion failed (orphaned asset): publicId={} error={}",
                    cloudinaryPublicId, e.getMessage());
        }
    }

    @Override
    public ImageResponse.Transformations buildTransformationUrls(String cloudinaryPublicId) {
        return ImageResponse.Transformations.builder()
                .thumbnail(buildUrl(cloudinaryPublicId, TRANSFORM_THUMBNAIL))
                .card(buildUrl(cloudinaryPublicId, TRANSFORM_CARD))
                .detail(buildUrl(cloudinaryPublicId, TRANSFORM_DETAIL))
                .zoom(buildUrl(cloudinaryPublicId, TRANSFORM_ZOOM))
                .build();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> performUpload(MultipartFile file, String folder) {
        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder",          folder,
                    "eager",           List.of(
                            new Transformation().width(200).height(250).crop("fill").gravity("auto").quality("auto").fetchFormat("auto"),
                            new Transformation().width(400).height(500).crop("fill").gravity("auto").quality("auto").fetchFormat("auto"),
                            new Transformation().width(800).height(1000).crop("fill").gravity("auto").quality("auto").fetchFormat("auto"),
                            new Transformation().width(1600).height(2000).crop("limit").quality("auto").fetchFormat("auto")
                    ),
                    "eager_async",     true,
                    "allowed_formats", List.of("jpg", "jpeg", "png", "webp")
            );

            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            log.info("Cloudinary upload successful: publicId={} bytes={}", result.get("public_id"), file.getSize());
            return result;

        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw new ImageUploadException("Image upload failed. Please try again.", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported image format. Allowed formats: JPG, JPEG, PNG, WebP. Received: " + contentType);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Image file size exceeds the 5 MB limit. Received: " + (file.getSize() / 1024 / 1024) + " MB.");
        }
    }

    private String buildUrl(String publicId, String transformation) {
        return String.format("https://res.cloudinary.com/%s/image/upload/%s/%s", cloudName, transformation, publicId);
    }

    private String buildGalleryFolder(Long productId) {
        return String.format("ego/%s/products/%d/gallery", environment, productId);
    }

    private String buildVariantFolder(Long productId, Long variantId) {
        return String.format("ego/%s/products/%d/variants/%d", environment, productId, variantId);
    }
}
