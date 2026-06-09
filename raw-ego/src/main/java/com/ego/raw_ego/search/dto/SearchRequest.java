package com.ego.raw_ego.search.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Request parameters for the faceted product search endpoint.
 *
 * <p>All fields are optional — an empty request returns all active products.
 * Multiple filters are ANDed together. Multiple values within a single filter
 * (e.g. sizes=["M","L"]) are ORed.
 *
 * <p><b>Category filtering (3-level hierarchy):</b><br>
 * Pass {@code categorySlug} with any slug at any depth — ROOT, GROUP, or LEAF.
 * The backend filters on the {@code categorySlugPath} keyword array in Elasticsearch,
 * so querying {@code categorySlug=men} returns ALL products under MEN (all groups, all leaves).
 * Querying {@code categorySlug=topwear} returns all products in every leaf under Topwear.
 * Querying {@code categorySlug=t-shirts} returns exactly T-Shirts products.
 * No client-side ID resolution required.
 */
@Data
@Builder
public class SearchRequest {

    /**
     * Full-text search query.
     * Searched across {@code name^5, categoryName^2, tags^2, description}.
     * Null or blank = match-all.
     */
    private String query;

    /**
     * Category filter by slug — works at any depth (ROOT, GROUP, or LEAF).
     * Uses a {@code term} filter on the {@code categorySlugPath} keyword array.
     * Null = all categories.
     */
    private String categorySlug;

    /** Filter by one or more size values (e.g. ["M", "L", "XL"]). ORed. */
    private List<String> sizes;

    /** Filter by one or more color display names (e.g. ["Black", "White"]). ORed. */
    private List<String> colors;

    /** Minimum price filter (inclusive). Applies to minPrice field on the document. */
    private Double minPrice;

    /** Maximum price filter (inclusive). Applies to maxPrice field on the document. */
    private Double maxPrice;

    /**
     * Sort order. Supported values:
     * <ul>
     *   <li>{@code createdAt,desc}   — Newest Arrivals (default)</li>
     *   <li>{@code minPrice,asc}     — Price: Low to High</li>
     *   <li>{@code minPrice,desc}    — Price: High to Low</li>
     *   <li>{@code avgRating,desc}   — Top Rated</li>
     * </ul>
     */
    @Builder.Default
    private String sort = "createdAt,desc";

    /** Zero-indexed page number. */
    @Builder.Default
    private int page = 0;

    /** Page size. Capped at {@code ego.search.max-page-size} (default 100). */
    @Builder.Default
    private int size = 24;
}
