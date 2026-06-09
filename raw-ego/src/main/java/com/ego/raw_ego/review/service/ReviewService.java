package com.ego.raw_ego.review.service;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.util.HtmlSanitizer;
import com.ego.raw_ego.review.dto.request.CreateReviewRequest;
import com.ego.raw_ego.review.dto.response.ProductRatingSummary;
import com.ego.raw_ego.review.dto.response.ReviewResponse;
import com.ego.raw_ego.review.entity.ProductReview;
import com.ego.raw_ego.review.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the product reviews module.
 *
 * <h3>Purchase gate</h3>
 * <p>A review can only be submitted if the authenticated user has at least one
 * {@code DELIVERED} order containing a variant of the target product.
 * This is checked via {@link ProductReviewRepository#hasUserPurchasedProduct}.
 *
 * <h3>One review per product</h3>
 * <p>Checked proactively via {@link ProductReviewRepository#existsByUserIdAndProductId}
 * before the insert to return a clean {@code 409 Conflict} with a user-friendly message.
 *
 * <h3>Moderation</h3>
 * <p>Reviews are auto-approved on submission. Admin may hard-delete any review
 * via {@code DELETE /api/v1/admin/reviews/{id}}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {

    private final ProductReviewRepository reviewRepository;

    // ── Customer: submit review ───────────────────────────────────────────────

    /**
     * Creates a new review for a product.
     *
     * @param productId the product being reviewed
     * @param user      the authenticated customer
     * @param request   rating, title, body
     * @return the persisted review as a response DTO
     * @throws ConflictException         if user has not purchased this product or already reviewed it
     * @throws ResourceNotFoundException if the productId does not exist (caught by FK violation upstream)
     */
    @Transactional
    public ReviewResponse createReview(Long productId, User user, CreateReviewRequest request) {

        // 1. Purchase gate — must have a DELIVERED order containing this product
        if (!reviewRepository.hasUserPurchasedProduct(user.getId(), productId)) {
            throw new ConflictException(
                    "You can only review products you have purchased and received. " +
                    "Please ensure your order for this product has been delivered.");
        }

        // 2. Duplicate guard — one review per product
        if (reviewRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new ConflictException(
                    "You have already submitted a review for this product.");
        }

        // 3. Sanitize user-supplied text fields to prevent stored XSS.
        //    Applied before persist — the DB stores clean content.
        //    Title: 200 char max; Body: 2000 char max.
        String sanitizedTitle = HtmlSanitizer.sanitize(request.getTitle(), 200);
        String sanitizedBody  = HtmlSanitizer.sanitize(request.getBody(),  2000);

        // 4. Persist
        ProductReview review = ProductReview.builder()
                .productId(productId)
                .userId(user.getId())
                .reviewerFirstName(user.getFirstName())
                .rating(request.getRating())
                .title(sanitizedTitle)
                .body(sanitizedBody)
                .build();

        ProductReview saved = reviewRepository.save(review);
        log.info("Review submitted: reviewId={} productId={} userId={} rating={}",
                saved.getId(), productId, user.getId(), request.getRating());

        return toResponse(saved);
    }

    // ── Public reads ─────────────────────────────────────────────────────────

    /**
     * Returns paginated reviews for a product, newest first.
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        return reviewRepository
                .findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::toResponse);
    }

    /**
     * Returns the aggregated rating summary for a product:
     * average rating, total count, and breakdown per star level (1–5).
     */
    @Transactional(readOnly = true)
    public ProductRatingSummary getRatingSummary(Long productId) {
        long reviewCount = reviewRepository.countByProductId(productId);
        Double rawAverage = reviewRepository.findAverageRatingByProductId(productId);

        // Round average to 1 decimal place for display
        Double averageRating = rawAverage == null ? null
                : Math.round(rawAverage * 10.0) / 10.0;

        // Build a complete breakdown map with all 5 star levels (0 if none)
        Map<Integer, Long> breakdown = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            breakdown.put(i, 0L);
        }
        List<Object[]> rawBreakdown = reviewRepository.findRatingBreakdownByProductId(productId);
        for (Object[] row : rawBreakdown) {
            int star = (int) row[0];
            long count = (long) row[1];
            breakdown.put(star, count);
        }

        return ProductRatingSummary.builder()
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .ratingBreakdown(breakdown)
                .build();
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /**
     * Admin hard-deletes a review by ID.
     *
     * @param reviewId the review to delete
     * @throws ResourceNotFoundException if the review does not exist
     */
    @Transactional
    public void adminDeleteReview(Long reviewId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found: id=" + reviewId));
        reviewRepository.delete(review);
        log.info("Review deleted by admin: reviewId={} productId={}", reviewId, review.getProductId());
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private ReviewResponse toResponse(ProductReview review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .rating(review.getRating())
                .title(review.getTitle())
                .body(review.getBody())
                .reviewerFirstName(review.getReviewerFirstName())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
