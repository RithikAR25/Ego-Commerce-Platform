# ADR-002: Search Indexing — Transactional Outbox Pattern

**Status:** Accepted  
**Date:** May 28, 2026  
**Decision-makers:** Engineering Team  
**Implemented by:** Antigravity AI Agent

---

## Context

Elasticsearch is a denormalized read store. It must be kept in sync with MySQL whenever a product is created, updated, activated, deactivated, or deleted. The naive approach — calling Elasticsearch directly in the product write transaction — has critical failure modes:

1. **ES failure = transaction rollback** — An ES timeout or network blip causes the product write to roll back, even though the business operation was valid.
2. **ES call inside `@Transactional` holds DB connection** — ES calls can take 100ms–2s. During this time the MySQL connection is held open. Under load this causes connection pool exhaustion.
3. **Crash between write and ES call** — If the server crashes after `productRepository.save()` but before the ES call, the product exists in MySQL but not in ES. No recovery mechanism.

The original implementation used `@Async ApplicationEvent`: publish an event after `save()`, handle in `@EventListener`. This solved problem #2 (async = no connection held) but not problems #1 or #3 (event is in-memory — lost on crash).

---

## Decision

Replace the `@Async ApplicationEvent` listener with the **Transactional Outbox Pattern**:

1. A `search_outbox` table is written **in the same MySQL transaction** as the product write.
2. A `@Scheduled` poller (`OutboxPoller`) runs every 5 seconds on a separate thread.
3. The poller reads up to 100 `PENDING` entries, calls `SearchIndexService.toDocument()` to build `ProductDocument` from MySQL (fresh read), bulk-upserts to Elasticsearch, then marks entries `DONE`.

```
Product write (MySQL @Transactional):
    ├── product_variants saved
    ├── inventory_records saved
    └── search_outbox entry saved (status=PENDING)
    ← commit

OutboxPoller (every 5s, independent thread):
    ├── SELECT * FROM search_outbox WHERE status='PENDING' LIMIT 100
    ├── For each entry: SearchIndexService.toDocument(productId)
    ├── ES Java Client: bulk index
    └── UPDATE search_outbox SET status='DONE'
```

---

## Alternatives Considered

### A. Direct ES call in `@Transactional`
- **Rejected:** Violates architecture rule #1 (no external HTTP inside `@Transactional`). Connection pool exhaustion risk.

### B. `@Async ApplicationEvent` listener
- **Was the original implementation**
- **Rejected for outbox:** Event lives in JVM heap — lost on crash, OOM, or deploy. No durability guarantee.

### C. Kafka / message queue
- **Rejected for current phase:** Adds operational complexity (Kafka cluster, consumer groups, schema registry). Outbox pattern achieves the same durability guarantee using infrastructure we already have (MySQL).
- Future consideration: if throughput requires it, outbox entries can be consumed by Kafka Connect with minimal code change.

### D. Nightly full reindex only
- **Rejected:** 24-hour ES lag is unacceptable for product availability changes (out-of-stock must disappear from search immediately).

---

## Consequences

### Positive
- Crash-safe: `search_outbox` entry survives server restart
- ES failure does not affect product write transactions
- DB connection never held during ES call
- `SearchReindexJob` (nightly + admin trigger) acts as safety net for any missed events
- Circuit breaker on ES adds further resilience (falls back to MySQL)

### Negative / Trade-offs
- Up to 5-second propagation delay from product write to ES visibility
- `search_outbox` table grows indefinitely — DONE entries need periodic cleanup (not yet implemented)
- `OutboxPoller` is a single node — not suitable for multi-instance deployment without distributed lock (acceptable for current single-instance deployment)

---

## Source References
- `com.ego.raw_ego.search.entity.SearchOutboxEntry` — outbox entity
- `com.ego.raw_ego.search.repository.SearchOutboxRepository` — `PENDING` query
- `com.ego.raw_ego.search.job.OutboxPoller` — `@Scheduled` poller
- `com.ego.raw_ego.search.service.SearchIndexService` — document builder
- `com.ego.raw_ego.search.job.SearchReindexJob` — full reindex job
