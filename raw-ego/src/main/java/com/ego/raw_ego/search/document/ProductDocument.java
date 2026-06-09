package com.ego.raw_ego.search.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * Elasticsearch document for the {@code products} index.
 *
 * <p>This is NOT a JPA entity — it is a denormalised, flat representation of a product
 * designed for fast full-text search, faceted filtering, and autocomplete.
 * MySQL remains the system of record; this document is rebuilt from MySQL data
 * by {@link com.ego.raw_ego.search.service.SearchIndexService}.
 *
 * <p><b>Custom analyzers (defined in product-index-settings.json):</b>
 * <ul>
 *   <li>{@code autocomplete_analyzer} on {@code name} — edge_ngram (2→15) for prefix matching</li>
 *   <li>{@code autocomplete_search_analyzer} at query time — standard tokenizer (no ngram)</li>
 *   <li>{@code search_analyzer} on {@code description} — lowercase + asciifolding</li>
 * </ul>
 *
 * <p><b>Category path fields:</b> {@code categoryPath} and {@code categorySlugPath} contain the
 * full ancestry of the product's leaf category, ordered root→group→leaf.
 * Example: {@code ["MEN", "Topwear", "T-Shirts"]} and {@code ["men", "topwear", "t-shirts"]}.
 * Using a {@code term} filter on these keyword arrays enables hierarchy-aware filtering:
 * querying {@code categorySlugPath=men} returns ALL products under MEN.
 *
 * <p><b>Visibility guard:</b> {@code isActive=false} for DRAFT and ARCHIVED products.
 * The {@link com.ego.raw_ego.search.service.SearchService} always filters on
 * {@code isActive=true} — DRAFT/ARCHIVED products never appear in search results.
 */
@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/product-index-settings.json")
@Mapping(mappingPath  = "elasticsearch/product-index-mappings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    @Id
    private Long id;

    /** Full product name. Uses autocomplete_analyzer for prefix search. */
    private String name;

    /** Product description. Uses search_analyzer (lowercase + asciifolding). */
    private String description;

    /** SEO slug — used to build storefront URLs in search results. */
    private String slug;

    /** FK to categories.id — the leaf category this product belongs to. */
    private Long categoryId;

    /** Display name of the LEAF category — used in field-boosted queries (name^2). */
    private String categoryName;

    /**
     * Full category ancestry as display names, ordered root→group→leaf.
     * Example: {@code ["MEN", "Topwear", "T-Shirts"]}.
     * Indexed as keyword array — enables hierarchy-aware filtering at any depth:
     * {@code term(categoryPath, "Topwear")} returns all Topwear products.
     */
    private List<String> categoryPath;

    /**
     * Full category ancestry as URL slugs, ordered root→group→leaf.
     * Example: {@code ["men", "topwear", "t-shirts"]}.
     * Used by the search filter endpoint — pass any slug to filter at that level.
     */
    private List<String> categorySlugPath;

    /** JSON tag array from Product.tags — indexed as keyword for exact faceting. */
    private List<String> tags;

    /**
     * Hero image URL — not indexed, only stored.
     * Returned in search results for the product card thumbnail.
     */
    private String primaryImageUrl;

    /**
     * Denormalised size values from all active variants (e.g. ["XS", "S", "M", "L"]).
     * Enables instant size facet filtering without joining tables.
     */
    private List<String> availableSizes;

    /**
     * Denormalised color display names from all active variants (e.g. ["Black", "White"]).
     * Enables instant color facet filtering.
     */
    private List<String> availableColors;

    /**
     * Hex codes corresponding to {@code availableColors} — same index order.
     * Not indexed — only stored for frontend swatch rendering.
     */
    private List<String> colorHexCodes;

    /** Lowest variant price across active variants — used for price range filter. */
    private Double minPrice;

    /** Highest variant price across active variants. */
    private Double maxPrice;

    /**
     * Total units available across all active variants.
     * Used for in-stock ranking boost (FunctionScoreQuery weight 1.5 when > 0).
     */
    private Integer totalStock;

    /** Average star rating (1.0–5.0). Pre-computed from product_reviews table. */
    private Float avgRating;

    /** Total number of reviews. */
    private Integer reviewCount;

    /**
     * Visibility flag — always false for DRAFT and ARCHIVED products.
     * SearchService ALWAYS filters on isActive=true.
     * This is the primary guard preventing hidden products from leaking into results.
     */
    private boolean isActive;

    /** Product creation timestamp — used for "Newest Arrivals" sort. */
    private Instant createdAt;
}
