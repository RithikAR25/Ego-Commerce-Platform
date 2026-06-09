package com.ego.raw_ego.search.job;

import com.ego.raw_ego.search.entity.SearchOutboxEntry;
import com.ego.raw_ego.search.entity.SearchOutboxEntry.EventType;
import com.ego.raw_ego.search.entity.SearchOutboxEntry.Status;
import com.ego.raw_ego.search.repository.SearchOutboxRepository;
import com.ego.raw_ego.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Background poller for the Transactional Outbox Pattern.
 *
 * <p>Runs every {@code ego.search.outbox.poll-interval-ms} (default 5s).
 * Fetches the oldest PENDING rows from {@code search_outbox}, processes them
 * in bulk via {@link SearchService}, then marks them DONE or FAILED.
 *
 * <p><b>Retry policy:</b> Failed rows are retried up to 3 times.
 * After 3 failures the row is marked FAILED and skipped indefinitely
 * (manual investigation required — check {@code error_message} column).
 *
 * <p><b>Ordering:</b> Rows are processed FIFO (oldest created_at first)
 * to maintain causal ordering of product updates.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int MAX_RETRIES = 3;

    private final SearchOutboxRepository outboxRepository;
    private final SearchService          searchService;

    @Value("${ego.search.outbox.batch-size:100}")
    private int batchSize;

    /**
     * Main poll cycle — runs on a fixed delay after the previous execution completes.
     * Fixed delay (not fixed rate) prevents pile-up if ES is slow.
     */
    @Scheduled(fixedDelayString = "${ego.search.outbox.poll-interval-ms:5000}")
    @Transactional
    public void processPendingOutbox() {
        List<SearchOutboxEntry> pending =
                outboxRepository.findPendingBatch(Status.PENDING, batchSize);

        if (pending.isEmpty()) return;

        log.debug("Outbox poll: {} PENDING rows", pending.size());

        // Separate UPSERT and DELETE operations
        List<SearchOutboxEntry> upserts = pending.stream()
                .filter(e -> e.getEventType() == EventType.UPSERT)
                .toList();
        List<SearchOutboxEntry> deletes = pending.stream()
                .filter(e -> e.getEventType() == EventType.DELETE)
                .toList();

        int doneCount   = 0;
        int failedCount = 0;

        // ── Bulk UPSERT ────────────────────────────────────────────────────
        if (!upserts.isEmpty()) {
            try {
                List<Long> ids = upserts.stream()
                        .map(SearchOutboxEntry::getProductId)
                        .distinct()
                        .collect(Collectors.toList());
                searchService.bulkIndex(ids);

                Instant now = Instant.now();
                upserts.forEach(e -> {
                    e.setStatus(Status.DONE);
                    e.setProcessedAt(now);
                });
                doneCount += upserts.size();

            } catch (Exception e) {
                log.error("Outbox bulk UPSERT failed: {}", e.getMessage());
                upserts.forEach(entry -> markFailed(entry, e.getMessage()));
                failedCount += upserts.size();
            }
        }

        // ── Individual DELETEs ─────────────────────────────────────────────
        for (SearchOutboxEntry entry : deletes) {
            try {
                searchService.deleteFromIndex(entry.getProductId());
                entry.setStatus(Status.DONE);
                entry.setProcessedAt(Instant.now());
                doneCount++;
            } catch (Exception e) {
                log.error("Outbox DELETE failed for product id={}: {}", entry.getProductId(), e.getMessage());
                markFailed(entry, e.getMessage());
                failedCount++;
            }
        }

        outboxRepository.saveAll(pending);

        if (doneCount > 0 || failedCount > 0) {
            log.info("Outbox poll complete: {} done, {} failed", doneCount, failedCount);
        }
    }

    private void markFailed(SearchOutboxEntry entry, String errorMessage) {
        int newRetryCount = entry.getRetryCount() + 1;
        entry.setRetryCount(newRetryCount);
        entry.setErrorMessage(errorMessage);

        if (newRetryCount >= MAX_RETRIES) {
            entry.setStatus(Status.FAILED);
            log.error("Outbox entry id={} productId={} marked FAILED after {} retries",
                    entry.getId(), entry.getProductId(), MAX_RETRIES);
        }
        // If retryCount < MAX_RETRIES, leave as PENDING — next poll will retry
    }
}
