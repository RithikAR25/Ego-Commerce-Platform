package com.ego.raw_ego.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/products/{productId}/reviews}.
 */
@Getter
@NoArgsConstructor
public class CreateReviewRequest {

    @NotNull(message = "Rating is required.")
    @Min(value = 1, message = "Rating must be at least 1.")
    @Max(value = 5, message = "Rating must be at most 5.")
    private Integer rating;

    @Size(max = 150, message = "Title must not exceed 150 characters.")
    private String title;

    @Size(max = 5000, message = "Review body must not exceed 5000 characters.")
    private String body;
}
