package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Optional metadata sent alongside an image upload as form fields.
 *
 * <p>The image binary arrives as a {@code MultipartFile} parameter named {@code file}.
 * This DTO carries the supplementary text fields in the same multipart request.
 *
 * <p>All fields are optional — the controller defaults them when absent.
 * Validation constraints are minimal because the image binary itself is the primary payload.
 */
@Getter
@Setter
public class ImageUploadRequest {

    /**
     * Accessibility alt text for screen readers and SEO.
     * Optional — defaults to an empty string when not provided.
     * Example: "Oversized Acid-Wash Tee — Front shot in Black"
     */
    @Size(max = 255, message = "Alt text must not exceed 255 characters")
    private String altText;

    /**
     * Display position in the image gallery/thumbnail strip.
     * Lower numbers display first. Defaults to 0 (appended to end is also valid).
     * Optional — when omitted, the service appends the image at the current end.
     */
    @Min(value = 0, message = "Display order must be 0 or greater")
    private Integer displayOrder;
}
