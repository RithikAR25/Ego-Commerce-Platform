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
 * Infrastructure service that wraps the Cloudinary Java SDK.
 *
 * <p><b>Responsibility boundary:</b> This service only handles Cloudinary I/O — uploading
 * and deleting files, and constructing CDN transformation URLs. It has NO database
 * interactions. All DB persistence is handled by {@link ProductImageService}.
 *
 * <p><b>Transaction boundary:</b> Methods here must NEVER be called inside an active
 * {@code @Transactional} context. Cloudinary HTTP calls can take 1–5 seconds and would
 * hold the database connection pool for that entire duration.
 * See {@code ARCHITECTURE_RULES.md §2 Transaction Management}.
 *
 * <p><b>Supported formats:</b> JPEG, JPG, PNG, WebP.
 * <b>Max upload size:</b> 5 MB (enforced by Spring multipart config + this validator).
 *
 * <p><b>Folder naming (LOCKED):</b>
 * <pre>
 *   Gallery images:  ego/{env}/products/{productId}/gallery/
 *   Variant images:  ego/{env}/products/{productId}/variants/{variantId}/
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CloudinaryService {

    /** Injected Cloudinary SDK bean from {@link com.ego.raw_ego.common.config.CloudinaryConfig}. */
    private final Cloudinary cloudinary;

    /** Active environment label — "dev" or "prod". Injected from application.properties. */
    @Value("${cloudinary.environment:dev}")
    private String environment;

    /** Cloudinary cloud name — used to construct CDN delivery URLs. */
    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    // ── Supported MIME types ──────────────────────────────────────────────────

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    /** 5 MB in bytes. */
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;

    // ── Cloudinary transformation strings ────────────────────────────────────

    /** 200×250, c_fill smart crop — cart, order summary thumbnails. */
    private static final String TRANSFORM_THUMBNAIL = "w_200,h_250,c_fill,g_auto,q_auto,f_auto";

    /** 400×500, c_fill smart crop — product listing grid cards. */
    private static final String TRANSFORM_CARD      = "w_400,h_500,c_fill,g_auto,q_auto,f_auto";

    /** 800×1000, c_fill smart crop — PDP main image hero slot. */
    private static final String TRANSFORM_DETAIL    = "w_800,h_1000,c_fill,g_auto,q_auto,f_auto";

    /** 1600×2000, c_limit (no upscale) — PDP lightbox zoom overlay. */
    private static final String TRANSFORM_ZOOM      = "w_1600,h_2000,c_limit,q_auto,f_auto";

    // ── Upload methods ────────────────────────────────────────────────────────

    /**
     * Uploads a product gallery image to Cloudinary.
     *
     * <p>Target folder: {@code ego/{env}/products/{productId}/gallery/}
     *
     * <p>Eager transformations are pre-generated asynchronously at upload time so
     * that the first request for each size is served from cache, not computed on-demand.
     *
     * @param file      the raw image file from the multipart request
     * @param productId the database ID of the product — used in the folder path
     * @return upload result map from Cloudinary containing {@code public_id}, {@code secure_url}
     * @throws ImageUploadException if validation fails or the Cloudinary API returns an error
     */
    public Map<String, Object> uploadProductGalleryImage(MultipartFile file, Long productId) {
        validateFile(file);

        String folder = buildGalleryFolder(productId);
        log.info("Uploading gallery image for product={} to folder={}", productId, folder);

        return performUpload(file, folder);
    }

    /**
     * Uploads a variant-specific image to Cloudinary.
     *
     * <p>Target folder: {@code ego/{env}/products/{productId}/variants/{variantId}/}
     *
     * @param file      the raw image file from the multipart request
     * @param productId the database ID of the parent product
     * @param variantId the database ID of the variant — used in the folder path
     * @return upload result map from Cloudinary
     * @throws ImageUploadException if validation fails or the Cloudinary API returns an error
     */
    public Map<String, Object> uploadVariantImage(MultipartFile file, Long productId, Long variantId) {
        validateFile(file);

        String folder = buildVariantFolder(productId, variantId);
        log.info("Uploading variant image for variant={} product={} to folder={}",
                variantId, productId, folder);

        return performUpload(file, folder);
    }

    /**
     * Deletes an image from Cloudinary by its public_id.
     *
     * <p>Called AFTER the database record is deleted. If Cloudinary deletion fails,
     * only a WARN is logged — we do not roll back the DB deletion, because a missing
     * Cloudinary asset is a data hygiene issue, not a consistency issue.
     *
     * @param cloudinaryPublicId the {@code public_id} returned at upload time
     */
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
            // Log and continue — DB record is already gone, orphaned Cloudinary asset is tolerable
            log.warn("Cloudinary deletion failed (orphaned asset): publicId={} error={}",
                    cloudinaryPublicId, e.getMessage());
        }
    }

    // ── Transformation URL builder ────────────────────────────────────────────

    /**
     * Constructs all four standard transformation URLs from a Cloudinary {@code public_id}.
     *
     * <p>URLs are constructed using the Cloudinary delivery URL pattern:
     * {@code https://res.cloudinary.com/{cloud}/image/upload/{transformation}/{public_id}}
     *
     * <p>Only the {@code public_id} is stored in the database. URLs are generated here
     * at response-time. If CDN delivery URLs ever need to change, this is the single
     * place to update — database records remain valid.
     *
     * @param cloudinaryPublicId the stored {@code public_id} value
     * @return a fully-populated {@link ImageResponse.Transformations} object
     */
    public ImageResponse.Transformations buildTransformationUrls(String cloudinaryPublicId) {
        return ImageResponse.Transformations.builder()
                .thumbnail(buildUrl(cloudinaryPublicId, TRANSFORM_THUMBNAIL))
                .card(buildUrl(cloudinaryPublicId, TRANSFORM_CARD))
                .detail(buildUrl(cloudinaryPublicId, TRANSFORM_DETAIL))
                .zoom(buildUrl(cloudinaryPublicId, TRANSFORM_ZOOM))
                .build();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Performs the actual Cloudinary upload call with standard parameters.
     *
     * <p>Eager transformations are listed as pipe-separated strings. Setting
     * {@code eager_async = true} means Cloudinary generates these in the background —
     * the upload call returns immediately with the original asset URL.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> performUpload(MultipartFile file, String folder) {
        try {
            // eager MUST be List<Transformation> — the SDK's buildEager(Util.java:139)
            // does a hard cast of each element to com.cloudinary.Transformation.
            // Passing String or List<String> causes ClassCastException.
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
            log.info("Cloudinary upload successful: publicId={} bytes={}",
                    result.get("public_id"), file.getSize());

            return result;

        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw new ImageUploadException("Image upload failed. Please try again.", e);
        }
    }


    /**
     * Validates file type and size before touching Cloudinary.
     * Fails fast with a clear error message rather than wasting an API call.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported image format. Allowed formats: JPG, JPEG, PNG, WebP. " +
                    "Received: " + contentType);
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Image file size exceeds the 5 MB limit. " +
                    "Received: " + (file.getSize() / 1024 / 1024) + " MB.");
        }
    }

    /**
     * Constructs the Cloudinary delivery URL with a transformation string injected.
     *
     * <p>Pattern: {@code https://res.cloudinary.com/{cloud}/image/upload/{transform}/{publicId}}
     */
    private String buildUrl(String publicId, String transformation) {
        return String.format(
                "https://res.cloudinary.com/%s/image/upload/%s/%s",
                cloudName, transformation, publicId
        );
    }

    // ── Folder path builders (LOCKED structure) ───────────────────────────────

    /**
     * Builds the gallery folder path: {@code ego/{env}/products/{productId}/gallery}
     */
    private String buildGalleryFolder(Long productId) {
        return String.format("ego/%s/products/%d/gallery", environment, productId);
    }

    /**
     * Builds the variant folder path: {@code ego/{env}/products/{productId}/variants/{variantId}}
     */
    private String buildVariantFolder(Long productId, Long variantId) {
        return String.format("ego/%s/products/%d/variants/%d", environment, productId, variantId);
    }
}
