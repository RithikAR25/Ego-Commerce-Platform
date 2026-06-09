package com.ego.raw_ego.review.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Aggregated rating summary for a product's review page.
 *
 * <p>Returned by {@code GET /api/v1/products/{productId}/reviews/summary}.
 *
 * <p>The {@code ratingBreakdown} map contains counts for each star level:
 * <pre>
 * {
 *   "averageRating": 4.3,
 *   "reviewCount": 47,
 *   "ratingBreakdown": {
 *     "5": 28,
 *     "4": 12,
 *     "3": 5,
 *     "2": 1,
 *     "1": 1
 *   }
 * }
 * </pre>
 */
@Getter
@Builder
public class ProductRatingSummary {

    /** Average star rating, rounded to 1 decimal. Null if no reviews exist. */
    private Double averageRating;

    /** Total number of reviews. */
    private long reviewCount;

    /**
     * Count per star level (keys "1"–"5").
     * Stars with zero reviews are included with count 0.
     */
    private Map<Integer, Long> ratingBreakdown;
}
