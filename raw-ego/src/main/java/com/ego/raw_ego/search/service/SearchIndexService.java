package com.ego.raw_ego.search.service;

import com.ego.raw_ego.search.document.ProductDocument;

import java.util.List;
import java.util.Optional;

/**
 * Contract for assembling Elasticsearch documents from MySQL catalog data.
 *
 * <p>This is the bridge between the MySQL catalog and the Elasticsearch index.
 * Responsible for loading product data with variants, images, and attribute values,
 * denormalising into flat arrays, and enforcing the visibility guard.
 *
 * <p><b>Visibility guard (CRITICAL):</b>
 * Only ACTIVE and OUT_OF_STOCK products get {@code isActive=true}.
 * DRAFT and ARCHIVED products are indexed with {@code isActive=false}.
 */
public interface SearchIndexService {

    /**
     * Builds an ES document for the given product ID by loading all data from MySQL.
     *
     * @param productId the product to index
     * @return populated {@link ProductDocument}, or empty if product not found
     */
    Optional<ProductDocument> buildDocument(Long productId);

    /**
     * Builds documents for a batch of product IDs.
     * Products not found in MySQL are silently skipped.
     */
    List<ProductDocument> buildDocuments(List<Long> productIds);
}
