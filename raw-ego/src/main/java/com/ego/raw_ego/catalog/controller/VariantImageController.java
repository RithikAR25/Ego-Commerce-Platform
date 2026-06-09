package com.ego.raw_ego.catalog.controller;

import com.ego.raw_ego.catalog.dto.request.ImageUploadRequest;
import com.ego.raw_ego.catalog.dto.request.ReorderImagesRequest;
import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import com.ego.raw_ego.catalog.service.VariantImageService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for variant-specific image management.
 *
 * <p>Variant images represent a single colorway — e.g. all photos of the "Black" hoodie.
 * The frontend swaps the main gallery when a color swatch is selected.
 * Size selection does NOT change images (LOCKED behavior).
 *
 * <p><b>Public endpoint:</b>
 * <pre>
 *   GET /api/v1/products/{productId}/variants/{variantId}/images   → variant images
 * </pre>
 *
 * <p><b>Admin endpoints (ROLE_ADMIN required):</b>
 * <pre>
 *   POST   /api/v1/admin/products/{productId}/variants/{variantId}/images              → upload
 *   DELETE /api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}    → delete
 *   PATCH  /api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}/primary → set primary
 *   PUT    /api/v1/admin/products/{productId}/variants/{variantId}/images/reorder       → reorder
 * </pre>
 */
@RestController
@Tag(name = "Variant Images", description = "Variant-specific color image upload and management")
@RequiredArgsConstructor
public class VariantImageController {

    private final VariantImageService variantImageService;

    // ── Public ───────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/products/{productId}/variants/{variantId}/images")
    @Operation(
        summary = "Get variant images",
        description = "Returns all images for a specific variant (colorway), ordered by display position. " +
                      "The primary image (hero) appears first. " +
                      "Includes transformation URLs for all standard display sizes."
    )
    public ResponseEntity<ApiResponse<List<ImageResponse>>> getVariantImages(
            @PathVariable Long productId,
            @PathVariable Long variantId) {
        return ResponseEntity.ok(ApiResponse.success(
                variantImageService.getVariantImages(variantId)));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @PostMapping(
        value    = "/api/v1/admin/products/{productId}/variants/{variantId}/images",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Upload variant image",
        description = "Uploads a color-specific image for a variant to Cloudinary. " +
                      "The first image uploaded to a variant is automatically set as the primary (hero) image. " +
                      "Accepted formats: JPG, JPEG, PNG, WebP. Maximum size: 5 MB."
    )
    public ResponseEntity<ApiResponse<ImageResponse>> uploadVariantImage(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Accessibility alt text (max 255 chars). Optional.")
            @RequestParam(required = false)
            @Size(max = 255, message = "Alt text must not exceed 255 characters")
            String altText,
            @Parameter(description = "Display position in the thumbnail strip (0-based). Optional — appended to end if omitted.")
            @RequestParam(required = false)
            @Min(value = 0, message = "Display order must be 0 or greater")
            Integer displayOrder) {

        ImageUploadRequest request = new ImageUploadRequest();
        request.setAltText(altText);
        request.setDisplayOrder(displayOrder);

        ImageResponse response = variantImageService.uploadAndSaveVariantImage(
                productId, variantId, file, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Variant image uploaded successfully.", response));
    }

    @PatchMapping("/api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}/primary")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Set primary variant image",
        description = "Promotes the specified image to the primary (hero) position for this variant. " +
                      "The previous primary image is automatically demoted. " +
                      "The primary image is shown in product listing cards and the PDP main slot " +
                      "when this color variant is selected."
    )
    public ResponseEntity<ApiResponse<ImageResponse>> setPrimaryVariantImage(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @PathVariable Long imageId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Primary image updated.",
                variantImageService.setPrimaryVariantImage(variantId, imageId, productId)));
    }

    @DeleteMapping("/api/v1/admin/products/{productId}/variants/{variantId}/images/{imageId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Delete variant image",
        description = "Deletes the image from the database and removes it from Cloudinary CDN. " +
                      "If the deleted image was the primary, the next image (by display order) " +
                      "is automatically promoted to primary."
    )
    public ResponseEntity<ApiResponse<Void>> deleteVariantImage(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @PathVariable Long imageId) {
        variantImageService.deleteVariantImage(variantId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Variant image deleted successfully."));
    }

    @PutMapping("/api/v1/admin/products/{productId}/variants/{variantId}/images/reorder")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Reorder variant images",
        description = "Bulk-updates display order for all images of a variant. " +
                      "Send the complete new ordering as a list of {imageId, displayOrder} pairs. " +
                      "All imageIds must belong to the specified variant."
    )
    public ResponseEntity<ApiResponse<List<ImageResponse>>> reorderVariantImages(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody ReorderImagesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Variant images reordered successfully.",
                variantImageService.reorderVariantImages(variantId, request)));
    }
}
