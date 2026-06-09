package com.ego.raw_ego.search.dto;

import com.ego.raw_ego.catalog.dto.response.ProductSummaryResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response envelope for faceted product search.
 *
 * <p>Contains the matching product page, aggregation facets for filter panels,
 * and a {@code fallbackMode} flag that the frontend uses to display a
 * "Limited search available" banner when Elasticsearch was unavailable
 * and results were served from MySQL instead.
 */
@Data
@Builder
public class FacetedSearchResponse {

    /** Matching products for the current page. */
    private List<ProductSummaryResponse> content;

    /** Total matching documents (across all pages). */
    private long totalElements;

    /** Total pages at the requested page size. */
    private int totalPages;

    /** Aggregation facets for the filter panel. */
    private SearchFacets facets;

    /**
     * True when Elasticsearch was unavailable and results were served from MySQL.
     * The frontend should show a "Limited search available" banner.
     */
    @Builder.Default
    private boolean fallbackMode = false;

    // ── Nested DTOs ──────────────────────────────────────────────────────────

    /** Aggregation results for the left-panel filter UI. */
    @Data
    @Builder
    public static class SearchFacets {
        private List<FacetBucket> sizes;
        private List<FacetBucket> colors;
        private PriceStats priceStats;
    }

    /** A single bucket in a terms aggregation (e.g. size "M" with 42 matching products). */
    @Data
    @Builder
    public static class FacetBucket {
        private String value;
        private long count;
    }

    /** Price statistics from the stats aggregation — used to initialise the range slider. */
    @Data
    @Builder
    public static class PriceStats {
        private double min;
        private double max;
        private double avg;
    }
}
