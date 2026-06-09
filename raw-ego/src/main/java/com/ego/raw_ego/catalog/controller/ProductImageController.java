package com.ego.raw_ego.catalog.controller;

import com.ego.raw_ego.catalog.dto.request.ImageUploadRequest;
import com.ego.raw_ego.catalog.dto.request.ReorderImagesRequest;
import com.ego.raw_ego.catalog.dto.response.ImageResponse;
import com.ego.raw_ego.catalog.service.ProductImageService;
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
 * REST controller for product gallery image management.
 *
 * <p><b>Public endpoint:</b>
 * <pre>
 *   GET /api/v1/products/{productId}/images   → fetch gallery images (storefront)
 * </pre>
 *
 * <p><b>Admin endpoints (ROLE_ADMIN required):</b>
 * <pre>
 *   POST   /api/v1/admin/products/{productId}/images                  → upload gallery image
 *   DELETE /api/v1/admin/products/{productId}/images/{imageId}         → delete image
 *   PUT    /api/v1/admin/products/{productId}/images/reorder           → reorder images
 * </pre>
 *
 * <p>Security routing: admin paths fall under {@code /api/v1/admin/**} which is
 * protected by {@code hasRole('ADMIN')} in SecurityConfig without modification.
 */
@RestController
@Tag(name = "Product Images", description = "Product gallery image upload and management")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    // ── Public ───────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/products/{productId}/images")
    @Operation(
        summary = "Get product gallery images",
        description = "Returns all gallery images for a product ordered by display position. " +
                      "Includes transformation URLs for all standard display sizes."
    )
    public ResponseEntity<ApiResponse<List<ImageResponse>>> getProductImages(
            @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(
                productImageService.getProductImages(productId)));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @PostMapping(
        value    = "/api/v1/admin/products/{productId}/images",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Upload product gallery image",
        description = "Uploads an image to Cloudinary and registers it in the product gallery. " +
                      "Accepted formats: JPG, JPEG, PNG, WebP. Maximum size: 5 MB. " +
                      "altText and displayOrder are optional form fields sent alongside the file."
    )
    public ResponseEntity<ApiResponse<ImageResponse>> uploadProductImage(
            @PathVariable Long productId,
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Accessibility alt text (max 255 chars). Optional.")
            @RequestParam(required = false)
            @Size(max = 255, message = "Alt text must not exceed 255 characters")
            String altText,
            @Parameter(description = "Display position in the gallery (0-based). Optional — appended to end if omitted.")
            @RequestParam(required = false)
            @Min(value = 0, message = "Display order must be 0 or greater")
            Integer displayOrder) {

        ImageUploadRequest request = new ImageUploadRequest();
        request.setAltText(altText);
        request.setDisplayOrder(displayOrder);

        ImageResponse response = productImageService.uploadAndSaveProductImage(productId, file, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded successfully.", response));
    }

    @DeleteMapping("/api/v1/admin/products/{productId}/images/{imageId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Delete product gallery image",
        description = "Deletes the image from the database and removes it from Cloudinary CDN. " +
                      "The image must belong to the specified product."
    )
    public ResponseEntity<ApiResponse<Void>> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        productImageService.deleteProductImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Image deleted successfully."));
    }

    @PutMapping("/api/v1/admin/products/{productId}/images/reorder")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Reorder product gallery images",
        description = "Bulk-updates display order for all product gallery images. " +
                      "Send the complete new ordering as a list of {imageId, displayOrder} pairs. " +
                      "All imageIds must belong to the specified product."
    )
    public ResponseEntity<ApiResponse<List<ImageResponse>>> reorderProductImages(
            @PathVariable Long productId,
            @Valid @RequestBody ReorderImagesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Images reordered successfully.",
                productImageService.reorderProductImages(productId, request)));
    }
}
