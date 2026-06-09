package com.ego.raw_ego.review.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Single review response — used in paginated list views and admin review management.
 *
 * <p>Reviewer identity is limited to first name only for privacy.
 * User ID and email are never exposed in public review responses.
 */
@Getter
@Builder
public class ReviewResponse {

    private Long id;

    /** Star rating 1–5. */
    private int rating;

    /** Optional short headline. */
    private String title;

    /** Optional full review body. */
    private String body;

    /** Reviewer's first name (privacy-safe display name). */
    private String reviewerFirstName;

    /** When the review was submitted. */
    private Instant createdAt;
}
