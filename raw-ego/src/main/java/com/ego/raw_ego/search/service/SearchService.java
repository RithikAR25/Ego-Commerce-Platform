package com.ego.raw_ego.search.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import com.ego.raw_ego.catalog.dto.response.ProductSummaryResponse;
import com.ego.raw_ego.catalog.enums.ProductStatus;
import com.ego.raw_ego.catalog.repository.ProductRepository;
import com.ego.raw_ego.search.document.ProductDocument;
import com.ego.raw_ego.search.dto.FacetedSearchResponse;
import com.ego.raw_ego.search.dto.FacetedSearchResponse.FacetBucket;
import com.ego.raw_ego.search.dto.FacetedSearchResponse.PriceStats;
import com.ego.raw_ego.search.dto.FacetedSearchResponse.SearchFacets;
import com.ego.raw_ego.search.dto.SearchRequest;
import com.ego.raw_ego.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core search service — provides faceted search, autocomplete, and index management.
 *
 * <h3>Relevance tuning</h3>
 * <ul>
 *   <li>Multi-match: {@code name^5, categoryName^2, tags^2, description^1}</li>
 *   <li>FunctionScore boost: {@code weight=1.5} for in-stock products (totalStock > 0)</li>
 * </ul>
 *
 * <h3>Circuit breaker / graceful degradation</h3>
 * All ES calls are wrapped in try/catch. On failure:
 * <ul>
 *   <li>Logs a WARN</li>
 *   <li>Falls back to MySQL via {@link ProductRepository}</li>
 *   <li>Sets {@code fallbackMode=true} in the response</li>
 * </ul>
 *
 * <h3>Visibility guard</h3>
 * Every query includes an {@code isActive=true} filter.
 * DRAFT and ARCHIVED products are never returned.
 *
 * <h3>Query protection</h3>
 * Page size is capped at {@code ego.search.max-page-size}.
 * ES query timeout is set to 5 seconds.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations  esOperations;
    private final ProductSearchRepository  searchRepository;
    private final SearchIndexService       indexService;
    private final ProductRepository        productRepository;

    @Value("${ego.search.max-page-size:100}")
    private int maxPageSize;

    // ── Index management ─────────────────────────────────────────────────────

    /**
     * Upserts a single product document. Fetches data from MySQL via SearchIndexService.
     */
    public void indexProduct(Long productId) {
        indexService.buildDocument(productId).ifPresentOrElse(
            doc -> {
                searchRepository.save(doc);
                log.debug("Indexed product id={}", productId);
            },
            () -> log.warn("indexProduct: product id={} not found in MySQL — skipping", productId)
        );
    }

    /**
     * Removes a product document from the index (e.g. on ARCHIVED status).
     */
    public void deleteFromIndex(Long productId) {
        searchRepository.deleteById(productId);
        log.debug("Deleted product id={} from index", productId);
    }

    /**
     * Bulk-indexes a list of product IDs. Uses saveAll for batch efficiency.
     * Partial failures are logged but do not abort the rest of the batch.
     */
    public void bulkIndex(List<Long> productIds) {
        if (productIds.isEmpty()) return;

        List<ProductDocument> docs = indexService.buildDocuments(productIds);
        if (docs.isEmpty()) return;

        try {
            searchRepository.saveAll(docs);
            log.info("Bulk indexed {} products", docs.size());
        } catch (Exception e) {
            log.error("Bulk index failed for {} products: {}", docs.size(), e.getMessage());
            throw e;
        }
    }

    /**
     * Full reindex from MySQL — pages through all ACTIVE/OUT_OF_STOCK products
     * in batches of 100. Used by the nightly job and admin manual trigger.
     *
     * @return total number of documents indexed
     */
    public int reindexAll() {
        log.info("Full reindex started");
        long startMs = System.currentTimeMillis();
        int total    = 0;
        int page     = 0;

        List<ProductStatus> publicStatuses = List.of(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK);

        while (true) {
            Pageable pageable = PageRequest.of(page, 100);
            Page<com.ego.raw_ego.catalog.entity.Product> batch =
                    productRepository.findByStatusInOrderByCreatedAtDesc(publicStatuses, pageable);

            if (batch.isEmpty()) break;

            List<Long> ids = batch.getContent().stream()
                    .map(com.ego.raw_ego.catalog.entity.Product::getId)
                    .toList();
            bulkIndex(ids);

            total += ids.size();
            page++;
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("Full reindex complete: {} documents in {}ms", total, durationMs);
        return total;
    }

    // ── Search ───────────────────────────────────────────────────────────────

    /**
     * Executes a faceted search query against Elasticsearch.
     * Falls back to MySQL on any ES failure.
     */
    public FacetedSearchResponse search(SearchRequest req) {
        // Enforce max page size
        int safeSize = Math.min(req.getSize(), maxPageSize);
        req.setSize(safeSize);

        try {
            return executeEsSearch(req);
        } catch (Exception e) {
            log.warn("Elasticsearch unavailable — falling back to MySQL search. Error: {}", e.getMessage());
            return mysqlFallback(req);
        }
    }

    /**
     * Returns up to 5 product name suggestions for autocomplete.
     * Uses the edge_ngram autocomplete_analyzer on the name field.
     * Falls back to an empty list on ES failure.
     */
    public List<String> autocomplete(String q) {
        if (q == null || q.isBlank() || q.length() < 2) return List.of();

        try {
            Query boolQuery = Query.of(qb -> qb.bool(b -> b
                .must(m -> m.match(mm -> mm
                    .field("name")
                    .query(q.toLowerCase())
                    .analyzer("autocomplete_search_analyzer")))
                .filter(f -> f.term(t -> t.field("isActive").value(true)))
            ));

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(boolQuery)
                    .withPageable(PageRequest.of(0, 5))
                    .withSourceFilter(new FetchSourceFilter(true, new String[]{"name"}, null))
                    .build();

            SearchHits<ProductDocument> hits = esOperations.search(nativeQuery, ProductDocument.class);
            return hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .map(ProductDocument::getName)
                    .distinct()
                    .toList();

        } catch (Exception e) {
            log.warn("Autocomplete ES error: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Private: ES search implementation ────────────────────────────────────

    private FacetedSearchResponse executeEsSearch(SearchRequest req) {
        // ── Build bool query ────────────────────────────────────────────────
        BoolQuery.Builder bool = new BoolQuery.Builder();

        // Visibility guard — ALWAYS applied
        bool.filter(f -> f.term(t -> t.field("isActive").value(true)));

        // Full-text: multi-match with field boosting
        if (req.getQuery() != null && !req.getQuery().isBlank()) {
            bool.must(m -> m.multiMatch(mm -> mm
                .query(req.getQuery())
                .fields("name^5", "categoryName^2", "tags^2", "description")
                .type(TextQueryType.BestFields)
                .fuzziness("AUTO")));
        } else {
            bool.must(m -> m.matchAll(ma -> ma));
        }

        // Category filter — slug-based, works at any depth (ROOT / GROUP / LEAF).
        // Uses a term filter on the categorySlugPath keyword array.
        // Querying "men" returns all products whose path contains "men" (all MEN leaves).
        // Querying "topwear" returns all leaves under Topwear.
        // Querying "t-shirts" returns exactly T-Shirts products.
        if (req.getCategorySlug() != null && !req.getCategorySlug().isBlank()) {
            bool.filter(f -> f.term(t -> t.field("categorySlugPath").value(req.getCategorySlug().toLowerCase())));
        }

        // Size filter (OR within sizes)
        if (req.getSizes() != null && !req.getSizes().isEmpty()) {
            List<co.elastic.clients.elasticsearch._types.FieldValue> sizeValues = req.getSizes().stream()
                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList();
            bool.filter(f -> f.terms(t -> t.field("availableSizes")
                    .terms(tv -> tv.value(sizeValues))));
        }

        // Color filter (OR within colors)
        if (req.getColors() != null && !req.getColors().isEmpty()) {
            List<co.elastic.clients.elasticsearch._types.FieldValue> colorValues = req.getColors().stream()
                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList();
            bool.filter(f -> f.terms(t -> t.field("availableColors")
                    .terms(tv -> tv.value(colorValues))));
        }

        // Price range filter — overlapping range logic:
        //   maxPrice >= userMin  (product has a variant at or above user's floor)
        //   minPrice <= userMax  (product has a variant at or below user's ceiling)
        // This is correct for "any variant in range" semantics.
        // Previous bug: filtered only on minPrice, excluding products whose cheapest
        // variant is below the floor but whose most expensive variant is within range.
        if (req.getMinPrice() != null || req.getMaxPrice() != null) {
            final Double minP = req.getMinPrice();
            final Double maxP = req.getMaxPrice();
            if (minP != null) {
                // At least one variant priced at or above the user's minimum
                bool.filter(f -> f.range(r -> r.number(n -> n.field("maxPrice").gte(minP))));
            }
            if (maxP != null) {
                // At least one variant priced at or below the user's maximum
                bool.filter(f -> f.range(r -> r.number(n -> n.field("minPrice").lte(maxP))));
            }
        }

        // ── In-stock ranking boost ──────────────────────────────────────────
        // Products with totalStock > 0 get a 1.5x relevance boost
        Query baseQuery = Query.of(qb -> qb.bool(bool.build()));
        Query scoredQuery = Query.of(qb -> qb.functionScore(fs -> fs
            .query(baseQuery)
            .functions(fn -> fn
                .filter(filt -> filt.range(r -> r.number(n -> n.field("totalStock").gt(0.0))))
                .weight(1.5))
            .boostMode(FunctionBoostMode.Multiply)
            .scoreMode(FunctionScoreMode.Sum)
        ));

        // ── Build NativeQuery ────────────────────────────────────────────────
        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(scoredQuery)
                .withPageable(PageRequest.of(req.getPage(), req.getSize()));

        // Sort
        applySortOrder(queryBuilder, req.getSort());

        // ── Aggregations ────────────────────────────────────────────────────
        queryBuilder
            .withAggregation("sizes",       Aggregation.of(a -> a.terms(t -> t.field("availableSizes").size(30))))
            .withAggregation("colors",      Aggregation.of(a -> a.terms(t -> t.field("availableColors").size(20))))
            // priceStats: stats over minPrice → provides min (cheapest entry) and avg
            .withAggregation("priceStats",  Aggregation.of(a -> a.stats(s -> s.field("minPrice"))))
            // maxPriceAgg: separate max over maxPrice → true upper bound across all products
            // Previous bug: priceStats was the only aggregation, so priceStats.max = max(minPrice),
            // not max(maxPrice). This caused e.g. priceStats.max=999 for a product with maxPrice=1500.
            .withAggregation("maxPriceAgg", Aggregation.of(a -> a.max(m -> m.field("maxPrice"))));

        // ── Execute with timeout ─────────────────────────────────────────────
        NativeQuery nativeQuery = queryBuilder
                .withTimeout(Duration.ofSeconds(5))
                .build();

        SearchHits<ProductDocument> hits = esOperations.search(nativeQuery, ProductDocument.class);

        // ── Map results ──────────────────────────────────────────────────────
        List<ProductSummaryResponse> content = hits.getSearchHits().stream()
                .map(hit -> documentToSummary(hit.getContent()))
                .toList();

        long totalElements = hits.getTotalHits();
        int  totalPages    = req.getSize() > 0
                ? (int) Math.ceil((double) totalElements / req.getSize())
                : 0;

        // ── Extract facets ───────────────────────────────────────────────────
        SearchFacets facets = extractFacets(hits);

        return FacetedSearchResponse.builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .facets(facets)
                .fallbackMode(false)
                .build();
    }

    // ── MySQL fallback ────────────────────────────────────────────────────────

    private FacetedSearchResponse mysqlFallback(SearchRequest req) {
        int safeSize = Math.min(req.getSize(), maxPageSize);
        Pageable pageable = PageRequest.of(req.getPage(), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<ProductStatus> publicStatuses = List.of(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK);
        Page<com.ego.raw_ego.catalog.entity.Product> page =
                productRepository.findByStatusInOrderByCreatedAtDesc(publicStatuses, pageable);

        List<ProductSummaryResponse> content = page.getContent().stream()
                .map(ProductSummaryResponse::from)
                .toList();

        return FacetedSearchResponse.builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .facets(emptyFacets())
                .fallbackMode(true)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applySortOrder(NativeQueryBuilder builder, String sort) {
        if (sort == null) sort = "createdAt,desc";
        String[] parts = sort.split(",");
        String field    = parts[0];
        SortOrder order = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? SortOrder.Asc : SortOrder.Desc;
        builder.withSort(s -> s.field(f -> f.field(field).order(order)));
    }

    private SearchFacets extractFacets(SearchHits<ProductDocument> hits) {
        try {
            var aggs = hits.getAggregations();
            if (!(aggs instanceof ElasticsearchAggregations esAggs)) return emptyFacets();

            // ElasticsearchAggregations.aggregations() returns List<ElasticsearchAggregation>
            List<ElasticsearchAggregation> aggList = esAggs.aggregations();

            // ElasticsearchAggregations.aggregations() returns List<ElasticsearchAggregation>
            // ElasticsearchAggregation is a record: name() + aggregation()
            // aggregation() returns ElasticsearchAggregationContainer which has aggregate()
            Map<String, Aggregate> aggMap = aggList.stream()
                    .collect(Collectors.toMap(
                        ea -> ea.aggregation().getName(),
                        ea -> ea.aggregation().getAggregate()
                    ));

            List<FacetBucket> sizes  = extractTermBuckets(aggMap, "sizes");
            List<FacetBucket> colors = extractTermBuckets(aggMap, "colors");
            PriceStats priceStats    = extractPriceStats(aggMap, "priceStats", "maxPriceAgg");

            return SearchFacets.builder()
                    .sizes(sizes)
                    .colors(colors)
                    .priceStats(priceStats)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to extract facets: {}", e.getMessage());
            return emptyFacets();
        }
    }

    private List<FacetBucket> extractTermBuckets(Map<String, Aggregate> aggMap, String name) {
        try {
            Aggregate agg = aggMap.get(name);
            if (agg == null) return List.of();

            return agg.sterms().buckets().array().stream()
                    .map(b -> FacetBucket.builder()
                            .value(b.key().stringValue())
                            .count(b.docCount())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.debug("Could not extract term buckets for '{}': {}", name, e.getMessage());
            return List.of();
        }
    }

    /**
     * Extracts price stats from ES aggregations.
     *
     * @param aggMap     the ES aggregation result map
     * @param statsName  name of the stats aggregation (runs on {@code minPrice})
     * @param maxAggName name of the separate max aggregation (runs on {@code maxPrice})
     *
     * <p>Two separate aggregations are required because a single {@code stats} aggregation
     * on {@code minPrice} produces max=max(minPrice), not max(maxPrice). These differ when
     * the most expensive variant of any product is priced above the cheapest other product.
     */
    private PriceStats extractPriceStats(Map<String, Aggregate> aggMap,
                                          String statsName,
                                          String maxAggName) {
        try {
            Aggregate statsAgg = aggMap.get(statsName);
            if (statsAgg == null) return PriceStats.builder().min(0).max(0).avg(0).build();

            var stats = statsAgg.stats();
            double minVal = stats.min() != null ? stats.min() : 0.0;
            double avgVal = stats.avg() != null ? stats.avg() : 0.0;

            // Prefer the dedicated maxPrice aggregation for the true upper bound
            double maxVal = stats.max() != null ? stats.max() : 0.0;
            Aggregate maxAgg = aggMap.get(maxAggName);
            if (maxAgg != null) {
                Double esMax = maxAgg.max().value();
                if (esMax != null && !esMax.isNaN() && !esMax.isInfinite()) {
                    maxVal = esMax;
                }
            }

            return PriceStats.builder()
                    .min(minVal)
                    .max(maxVal)
                    .avg(avgVal)
                    .build();
        } catch (Exception e) {
            log.debug("Could not extract price stats: {}", e.getMessage());
            return PriceStats.builder().min(0).max(0).avg(0).build();
        }
    }

    private SearchFacets emptyFacets() {
        return SearchFacets.builder()
                .sizes(List.of())
                .colors(List.of())
                .priceStats(PriceStats.builder().min(0).max(0).avg(0).build())
                .build();
    }

    private ProductSummaryResponse documentToSummary(ProductDocument doc) {
        return ProductSummaryResponse.builder()
                .id(doc.getId())
                .name(doc.getName())
                .slug(doc.getSlug())
                .status(doc.isActive() ? ProductStatus.ACTIVE : ProductStatus.OUT_OF_STOCK)
                .minPrice(doc.getMinPrice() != null
                        ? java.math.BigDecimal.valueOf(doc.getMinPrice()) : null)
                .maxPrice(doc.getMaxPrice() != null
                        ? java.math.BigDecimal.valueOf(doc.getMaxPrice()) : null)
                .primaryImageUrl(doc.getPrimaryImageUrl())
                .tags(doc.getTags())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
