package com.ego.raw_ego.search.job;

import com.ego.raw_ego.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly full reindex job — runs at 3 AM every day.
 *
 * <p>This is the safety net for any sync drift between MySQL and Elasticsearch.
 * Even if all outbox events processed correctly, the nightly reindex catches:
 * <ul>
 *   <li>Inventory updates that occurred outside the normal service layer</li>
 *   <li>Review count/rating changes that weren't published to outbox</li>
 *   <li>Any data corrections applied directly to the database</li>
 * </ul>
 *
 * <p>Exposes a manual trigger via {@code POST /api/v1/admin/search/reindex}
 * for use after bulk catalog imports or debugging sessions.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SearchReindexJob {

    private final SearchService searchService;

    /**
     * Nightly reindex — pages through all ACTIVE/OUT_OF_STOCK products from MySQL
     * and bulk-upserts them to Elasticsearch.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void reindexAllNightly() {
        log.info("Nightly ES reindex started");
        try {
            int count = searchService.reindexAll();
            log.info("Nightly ES reindex completed: {} documents", count);
        } catch (Exception e) {
            log.error("Nightly ES reindex failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger — called by {@link com.ego.raw_ego.search.controller.SearchController}
     * on {@code POST /api/v1/admin/search/reindex}.
     *
     * @return number of products indexed
     */
    public int triggerManualReindex() {
        log.info("Manual ES reindex triggered by admin");
        return searchService.reindexAll();
    }
}
