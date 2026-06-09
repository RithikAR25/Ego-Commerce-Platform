package com.ego.raw_ego.search.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Outbox table entry for durable Elasticsearch sync events.
 *
 * <p><b>Transactional Outbox Pattern:</b>
 * When a product is created/updated/archived, the catalog service writes a row
 * here within the <em>same database transaction</em> as the Product update.
 * This guarantees that the indexing event is never lost — even on crash,
 * deployment, or temporary ES unavailability.
 *
 * <p><b>Background processing:</b>
 * {@link com.ego.raw_ego.search.job.OutboxPoller} polls this table every 5 seconds,
 * processes PENDING rows in bulk, then marks them DONE (or FAILED with retry info).
 *
 * <p><b>Replay support:</b>
 * To force re-indexing of a product, set its outbox row back to PENDING.
 * To replay all: {@code UPDATE search_outbox SET status='PENDING' WHERE status='FAILED';}
 */
@Entity
@Table(
    name = "search_outbox",
    indexes = {
        // Poller's primary query: PENDING rows oldest-first
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
        // Fast lookup by product
        @Index(name = "idx_outbox_product",         columnList = "product_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The product whose Elasticsearch document should be updated or deleted.
     * Raw FK — avoids importing Product entity into the search module.
     */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Whether this entry should trigger an ES upsert or a deletion. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 10)
    private EventType eventType;

    /** Processing state managed by the outbox poller. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.PENDING;

    /** Number of failed processing attempts. Max 3 — then FAILED permanently. */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** Last failure reason — aids debugging. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** When this event was written (tied to the originating MySQL transaction). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the poller successfully processed this entry. */
    @Column(name = "processed_at")
    private Instant processedAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum EventType {
        UPSERT,  // upsert product document into ES
        DELETE   // remove product document from ES
    }

    public enum Status {
        PENDING,  // waiting to be processed
        DONE,     // successfully synced to ES
        FAILED    // exhausted retries — requires manual investigation
    }
}
