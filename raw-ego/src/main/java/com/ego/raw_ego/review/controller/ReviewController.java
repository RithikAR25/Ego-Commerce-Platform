package com.ego.raw_ego.review.controller;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.response.ApiResponse;
import com.ego.raw_ego.review.dto.request.CreateReviewRequest;
import com.ego.raw_ego.review.dto.response.ProductRatingSummary;
import com.ego.raw_ego.review.dto.response.ReviewResponse;
import com.ego.raw_ego.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for product reviews.
 *
 * <pre>
 * ── Public (no auth) ──────────────────────────────────────────────────────
 * GET    /api/v1/products/{productId}/reviews          → paginated reviews
 * GET    /api/v1/products/{productId}/reviews/summary  → rating summary
 *
 * ── Customer (JWT required) ───────────────────────────────────────────────
 * POST   /api/v1/products/{productId}/reviews          → submit review
 *
 * ── Admin (JWT + ROLE_ADMIN) ──────────────────────────────────────────────
 * DELETE /api/v1/admin/reviews/{reviewId}              → hard-delete review
 * </pre>
 */
@RestController
@Tag(name = "Reviews", description = "Purchase-gated product reviews and rating summaries")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService     reviewService;
    private final UserRepository    userRepository;

    // ── Public: list reviews ──────────────────────────────────────────────────

    @GetMapping("/api/v1/products/{productId}/reviews")
    @Operation(
            summary     = "List product reviews",
            description = "Returns paginated reviews for a product, newest first. No authentication required."
    )
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getReviews(
            @PathVariable Long productId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ReviewResponse> page = reviewService.getProductReviews(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved.", page));
    }

    // ── Public: rating summary ────────────────────────────────────────────────

    @GetMapping("/api/v1/products/{productId}/reviews/summary")
    @Operation(
            summary     = "Get product rating summary",
            description = "Returns average rating, review count, and per-star breakdown. No authentication required."
    )
    public ResponseEntity<ApiResponse<ProductRatingSummary>> getRatingSummary(
            @PathVariable Long productId
    ) {
        ProductRatingSummary summary = reviewService.getRatingSummary(productId);
        return ResponseEntity.ok(ApiResponse.success("Rating summary retrieved.", summary));
    }

    // ── Customer: submit review ───────────────────────────────────────────────

    @PostMapping("/api/v1/products/{productId}/reviews")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary     = "Submit a product review",
            description = "Purchase-gated. User must have a DELIVERED order containing this product. " +
                          "One review per product per user. Auto-approved."
    )
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        User user = resolveUser(userDetails);
        ReviewResponse response = reviewService.createReview(productId, user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted successfully.", response));
    }

    // ── Admin: delete review ──────────────────────────────────────────────────

    @DeleteMapping("/api/v1/admin/reviews/{reviewId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary     = "Delete a review (admin)",
            description = "Hard-deletes a review. Requires ROLE_ADMIN."
    )
    public ResponseEntity<ApiResponse<Void>> adminDeleteReview(
            @PathVariable Long reviewId
    ) {
        reviewService.adminDeleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.success("Review deleted.", null));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmailAndDeletedFalse(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
