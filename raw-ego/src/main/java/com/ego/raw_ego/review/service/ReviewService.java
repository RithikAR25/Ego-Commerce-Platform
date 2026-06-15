package com.ego.raw_ego.review.service;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.review.dto.request.CreateReviewRequest;
import com.ego.raw_ego.review.dto.response.ProductRatingSummary;
import com.ego.raw_ego.review.dto.response.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for the product reviews module.
 *
 * <h3>Purchase gate</h3>
 * <p>A review can only be submitted if the authenticated user has at least one
 * {@code DELIVERED} order containing a variant of the target product.
 *
 * <h3>One review per product</h3>
 * <p>Duplicate reviews return a clean {@code 409 Conflict}.
 *
 * <h3>Moderation</h3>
 * <p>Reviews are auto-approved on submission. Admin may hard-delete any review.
 */
public interface ReviewService {

    /**
     * Creates a new review for a product.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if user has not purchased or already reviewed
     */
    ReviewResponse createReview(Long productId, User user, CreateReviewRequest request);

    /** Returns paginated reviews for a product, newest first. */
    Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable);

    /** Returns the aggregated rating summary (average, count, breakdown per star). */
    ProductRatingSummary getRatingSummary(Long productId);

    /**
     * Admin hard-deletes a review by ID.
     *
     * @throws com.ego.raw_ego.common.exception.ResourceNotFoundException if not found
     */
    void adminDeleteReview(Long reviewId);
}
