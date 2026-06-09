package com.ego.raw_ego.search.controller;

import com.ego.raw_ego.common.response.ApiResponse;
import com.ego.raw_ego.search.dto.FacetedSearchResponse;
import com.ego.raw_ego.search.dto.SearchRequest;
import com.ego.raw_ego.search.entity.SearchOutboxEntry;
import com.ego.raw_ego.search.job.SearchReindexJob;
import com.ego.raw_ego.search.repository.SearchOutboxRepository;
import com.ego.raw_ego.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Elasticsearch-powered product search.
 *
 * <pre>
 * GET  /api/v1/search                          → faceted product search
 * GET  /api/v1/search/autocomplete             → name prefix suggestions
 * POST /api/v1/admin/search/reindex            → admin manual full reindex
 * GET  /api/v1/admin/search/outbox/failed      → admin dead-letter queue viewer
 * </pre>
 */
@RestController
@Tag(name = "Search", description = "Elasticsearch-powered product search and autocomplete")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService          searchService;
    private final SearchReindexJob       reindexJob;
    private final SearchOutboxRepository outboxRepository;

    /**
     * Faceted product search.
     *
     * <p>All parameters are optional. An empty request returns all active products.
     * Filters are ANDed; values within a filter (sizes, colors) are ORed.
     */
    @GetMapping("/api/v1/search")
    @Operation(summary = "Faceted product search",
               description = "Full-text search with faceted filtering. Falls back to MySQL on ES failure. " +
                             "Category filter: pass categorySlug at any depth (ROOT/GROUP/LEAF slug). " +
                             "Example: categorySlug=men (all MEN products), categorySlug=t-shirts (exact leaf).")
    public ResponseEntity<ApiResponse<FacetedSearchResponse>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) List<String> sizes,
            @RequestParam(required = false) List<String> colors,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "24") int size) {

        SearchRequest req = SearchRequest.builder()
                .query(query)
                .categorySlug(categorySlug)
                .sizes(sizes)
                .colors(colors)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .sort(sort)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(ApiResponse.success(searchService.search(req)));
    }

    /**
     * Autocomplete suggestions for the search bar.
     * Returns up to 5 product name suggestions matching the given prefix.
     * Uses edge_ngram analysis — minimum 2 characters required.
     *
     * @param q the prefix to match (minimum 2 characters)
     */
    @GetMapping("/api/v1/search/autocomplete")
    @Operation(summary = "Search autocomplete",
               description = "Returns up to 5 product name suggestions for the given prefix.")
    public ResponseEntity<ApiResponse<List<String>>> autocomplete(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(searchService.autocomplete(q)));
    }

    /**
     * Trigger a full reindex of all ACTIVE/OUT_OF_STOCK products.
     * Admin only. Use after bulk catalog imports or for debugging sync issues.
     */
    @PostMapping("/api/v1/admin/search/reindex")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "[Admin] Trigger full Elasticsearch reindex",
               description = "Pages through all ACTIVE/OUT_OF_STOCK products and bulk-upserts to ES.")
    public ResponseEntity<ApiResponse<String>> reindex() {
        int count = reindexJob.triggerManualReindex();
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Reindex complete. %d products indexed.", count)));
    }

    /**
     * Dead-letter queue viewer for the search outbox.
     *
     * <p>Returns all {@code FAILED} outbox entries — rows that exhausted their 3-retry
     * limit and will no longer be auto-processed. Requires manual investigation:
     * review the {@code errorMessage} field, then trigger
     * {@code POST /api/v1/admin/search/reindex} to re-process the affected product IDs.
     *
     * <p>Admin only.
     */
    @GetMapping("/api/v1/admin/search/outbox/failed")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "[Admin] View failed ES outbox entries (dead-letter queue)",
               description = "Returns all outbox entries that exhausted retries. Check errorMessage, then trigger reindex.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFailedOutboxEntries() {
        long failedCount = outboxRepository.countByStatus(SearchOutboxEntry.Status.FAILED);
        List<SearchOutboxEntry> failedEntries = outboxRepository.findByStatus(SearchOutboxEntry.Status.FAILED);

        Map<String, Object> result = Map.of(
            "failedCount", failedCount,
            "entries", failedEntries.stream().map(e -> Map.of(
                "id",           e.getId(),
                "productId",    e.getProductId(),
                "eventType",    e.getEventType(),
                "retryCount",   e.getRetryCount(),
                "errorMessage", e.getErrorMessage() != null ? e.getErrorMessage() : "",
                "createdAt",    e.getCreatedAt()
            )).toList()
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
