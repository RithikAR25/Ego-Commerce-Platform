package com.ego.raw_ego.search.service;

import com.ego.raw_ego.search.dto.FacetedSearchResponse;
import com.ego.raw_ego.search.dto.SearchRequest;

import java.util.List;

/**
 * Contract for Elasticsearch search operations and index management.
 *
 * <h3>Relevance tuning</h3>
 * <ul>
 *   <li>Multi-match: {@code name^5, categoryName^2, tags^2, description^1}</li>
 *   <li>FunctionScore boost: {@code weight=1.5} for in-stock products (totalStock > 0)</li>
 * </ul>
 *
 * <h3>Circuit breaker / graceful degradation</h3>
 * All ES calls fall back to MySQL on failure, with {@code fallbackMode=true} in the response.
 *
 * <h3>Visibility guard</h3>
 * Every query includes an {@code isActive=true} filter. DRAFT and ARCHIVED products are never returned.
 */
public interface SearchService {

    /** Upserts a single product document. */
    void indexProduct(Long productId);

    /** Removes a product document from the index (e.g. on ARCHIVED status). */
    void deleteFromIndex(Long productId);

    /** Bulk-indexes a list of product IDs. */
    void bulkIndex(List<Long> productIds);

    /**
     * Full reindex from MySQL — pages through all ACTIVE/OUT_OF_STOCK products in batches of 100.
     *
     * @return total number of documents indexed
     */
    int reindexAll();

    /**
     * Executes a faceted search query against Elasticsearch.
     * Falls back to MySQL on any ES failure.
     */
    FacetedSearchResponse search(SearchRequest req);

    /**
     * Returns autocomplete suggestions for the given query prefix.
     * Falls back to an empty list on ES failure.
     */
    List<String> autocomplete(String q);
}
