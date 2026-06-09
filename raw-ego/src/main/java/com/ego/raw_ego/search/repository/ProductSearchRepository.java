package com.ego.raw_ego.search.repository;

import com.ego.raw_ego.search.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data Elasticsearch repository for {@link ProductDocument}.
 *
 * <p>Provides basic CRUD operations against the {@code products} ES index.
 * All complex queries (faceted search, autocomplete) use
 * {@link org.springframework.data.elasticsearch.core.ElasticsearchOperations}
 * directly in {@link com.ego.raw_ego.search.service.SearchService}.
 */
@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {
}
