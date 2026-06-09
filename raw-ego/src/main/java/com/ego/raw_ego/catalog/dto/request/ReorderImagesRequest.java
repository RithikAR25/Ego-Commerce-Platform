package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request payload for bulk image display order resequencing.
 *
 * <p>The admin drags-and-drops images in the UI. The frontend sends the new
 * complete ordering as a list of {@link ImageOrderEntry} records.
 * The service applies them atomically in a single transaction.
 *
 * <p>Example payload:
 * <pre>
 * {
 *   "order": [
 *     { "imageId": 5, "displayOrder": 0 },
 *     { "imageId": 3, "displayOrder": 1 },
 *     { "imageId": 7, "displayOrder": 2 }
 *   ]
 * }
 * </pre>
 */
@Getter
@Setter
public class ReorderImagesRequest {

    @NotNull(message = "Order list is required")
    @Size(min = 1, message = "Order list must contain at least one entry")
    @Valid
    private List<ImageOrderEntry> order;

    @Getter
    @Setter
    public static class ImageOrderEntry {

        @NotNull(message = "Image ID is required")
        private Long imageId;

        @NotNull(message = "Display order is required")
        @Min(value = 0, message = "Display order must be 0 or greater")
        private Integer displayOrder;
    }
}
