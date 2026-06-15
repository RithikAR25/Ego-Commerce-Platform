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
 * Implementation of {@link ReviewService} — business logic for product reviews.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ProductReviewRepository reviewRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(Long productId, User user, CreateReviewRequest request) {
        if (!reviewRepository.hasUserPurchasedProduct(user.getId(), productId)) {
            throw new ConflictException(
                    "You can only review products you have purchased and received. " +
                    "Please ensure your order for this product has been delivered.");
        }

        if (reviewRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new ConflictException("You have already submitted a review for this product.");
        }

        String sanitizedTitle = HtmlSanitizer.sanitize(request.getTitle(), 200);
        String sanitizedBody  = HtmlSanitizer.sanitize(request.getBody(),  2000);

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

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        return reviewRepository
                .findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductRatingSummary getRatingSummary(Long productId) {
        long reviewCount = reviewRepository.countByProductId(productId);
        Double rawAverage = reviewRepository.findAverageRatingByProductId(productId);

        Double averageRating = rawAverage == null ? null
                : Math.round(rawAverage * 10.0) / 10.0;

        Map<Integer, Long> breakdown = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            breakdown.put(i, 0L);
        }
        List<Object[]> rawBreakdown = reviewRepository.findRatingBreakdownByProductId(productId);
        for (Object[] row : rawBreakdown) {
            int star  = (int) row[0];
            long count = (long) row[1];
            breakdown.put(star, count);
        }

        return ProductRatingSummary.builder()
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .ratingBreakdown(breakdown)
                .build();
    }

    @Override
    @Transactional
    public void adminDeleteReview(Long reviewId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: id=" + reviewId));
        reviewRepository.delete(review);
        log.info("Review deleted by admin: reviewId={} productId={}", reviewId, review.getProductId());
    }

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
