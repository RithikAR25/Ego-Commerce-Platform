# EGO Platform — Documentation

> **Ground truth rule:** Source code (`raw-ego/`, `raw-ego-frontend/`) is the only truth.  
> All documentation is verified against source. When docs conflict with code, code wins.  
> Last verified: **June 6, 2026**

---

## Quick Navigation

| I want to... | Go here |
|---|---|
| Get the project running in 5 minutes | [01-getting-started/local-development.md](01-getting-started/local-development.md) |
| Understand the full architecture | [02-architecture/system-overview.md](02-architecture/system-overview.md) |
| Learn how a feature works | [03-features/](03-features/) |
| Trace a user flow end-to-end | [04-flows/](04-flows/) |
| Look up an API endpoint | [05-api/api-reference.md](05-api/api-reference.md) |
| Check the database schema | [06-database/schema-overview.md](06-database/schema-overview.md) |
| Understand Elasticsearch | [07-search/elasticsearch.md](07-search/elasticsearch.md) |
| Review security architecture | [08-security/](08-security/) |
| See test results + QA status | [09-testing/qa-execution-results.md](09-testing/qa-execution-results.md) |
| Debug a production issue | [10-troubleshooting/](10-troubleshooting/) |
| Deploy to production | [11-deployment/production-deployment.md](11-deployment/production-deployment.md) |
| **Onboard a new AI agent** | **[12-agents/](12-agents/)** ← Start here |
| Read architecture decisions | [13-decisions/](13-decisions/) |

---

## Project at a Glance

**EGO** is a premium D2C streetwear e-commerce platform. Full-stack:
- **Backend:** Spring Boot 4.0.6 · Java 21 · MySQL 8 · Redis 7 · Elasticsearch 9.0.1
- **Frontend:** React 19 · TypeScript · Vite 8 · MUI 9 · TanStack Query · Zustand
- **Payments:** Razorpay (Indian market — UPI, cards, netbanking)
- **Images:** Cloudinary CDN (4 transformation sizes per image)
- **Email:** SendGrid (async transactional emails)
- **Deployment:** Phase 15 pending (Docker Compose → VPS)

---

## Feature Index

| Feature | Docs | Status |
|---|---|---|
| [Authentication](03-features/authentication.md) | Backend + Frontend | ✅ Complete |
| [Categories (3-level)](03-features/categories.md) | Backend + Frontend + DB | ✅ Complete |
| [Products + Images](03-features/products.md) | Backend + Frontend + DB | ✅ Complete |
| [Search (Elasticsearch)](03-features/search.md) | Backend + ES | ✅ Complete |
| [Cart (Redis)](03-features/cart.md) | Backend + Frontend | ✅ Complete |
| [Checkout + Coupons](03-features/checkout.md) | Backend + Frontend | ✅ Complete |
| [Orders](03-features/orders.md) | Backend + Frontend + DB | ✅ Complete |
| [Payments (Razorpay)](03-features/payments.md) | Backend + Frontend | ✅ Complete |
| [Reviews](03-features/reviews.md) | Backend + DB | ✅ Complete |
| [Wishlist](03-features/wishlist.md) | Backend + DB | ✅ Complete |
| [Returns & Refunds](03-features/returns.md) | Backend + Frontend + DB | ✅ Complete |
| [Notifications (SendGrid)](03-features/notifications.md) | Backend | ✅ Complete |
| [Admin Portal](03-features/admin-portal.md) | Frontend | ✅ Complete |
| [Coupons](03-features/coupons.md) | Backend + DB | ✅ Complete |
| [Address Book](03-features/address-book.md) | Backend | ✅ Complete |

---

## Flow Diagrams

| Flow | Mermaid Diagram |
|---|---|
| [Authentication](04-flows/authentication.md) | Registration, boot restore, 401 interceptor, theft detection |
| [Checkout](04-flows/checkout-flow.md) | Cart → checkout → payment → order |
| [Order Lifecycle](04-flows/order-lifecycle.md) | Full 8-state machine, return state machine |

---

## Architecture Decision Records

| ADR | Decision |
|---|---|
| [ADR-001](13-decisions/architecture-decision-records/ADR-001-category-architecture.md) | 3-Level Category Hierarchy (ROOT → GROUP → LEAF) |
| [ADR-002](13-decisions/architecture-decision-records/ADR-002-search-outbox-pattern.md) | Search Indexing via Transactional Outbox |
| [ADR-003](13-decisions/architecture-decision-records/ADR-003-jwt-refresh-token-strategy.md) | JWT + Refresh Token Rotation Strategy |
| [ADR-004](13-decisions/architecture-decision-records/ADR-004-product-attribute-model.md) | Product Attribute EAV Model |

---

## AI Agent Onboarding (`docs/12-agents/`)

A new AI agent can understand the **entire project** from these 6 files alone:

| File | Contains |
|---|---|
| [project-context.md](12-agents/project-context.md) | Business purpose, repo structure, tech stack, env vars, run commands |
| [architecture-context.md](12-agents/architecture-context.md) | Backend lifecycle, frontend structure, routing, state model, data flows |
| [feature-context.md](12-agents/feature-context.md) | Feature-by-feature — what, where, rules, state machines, APIs |
| [coding-conventions.md](12-agents/coding-conventions.md) | Architecture rules, naming, error handling, security checklist |
| [qa-context.md](12-agents/qa-context.md) | Test results, resolved bugs, QA coverage, known limitations |
| [deployment-context.md](12-agents/deployment-context.md) | Infrastructure, env vars, production checklist, bootstrap sequence |

> **Note:** The existing `AGENT_CONTEXT/` directory at the repository root contains **implementation rules** for AI agents building features. `docs/12-agents/` provides **documentation-oriented** context. Both are authoritative — `AGENT_CONTEXT/ARCHITECTURE_RULES.md` and `AGENT_CONTEXT/IMPLEMENTATION_RULES.md` take precedence for implementation decisions.

---

## Documentation Standards

1. **Source code is the only truth** — every statement must be verifiable in `raw-ego/` or `raw-ego-frontend/`
2. **`⚠️ UNVERIFIED`** — any claim not verified against source must be marked with this prefix
3. **`⚠️ NOT YET IMPLEMENTED`** — any planned feature not yet in source must be marked clearly
4. **`⚠️ CONTRADICTION — fixed by code`** — if old docs conflict with source, mark and correct
5. All state machine values come from the actual enum source files — never from memory or old docs

---

## Directory Tree

```
docs/
├── README.md                          ← You are here
├── 01-getting-started/
├── 02-architecture/
├── 03-features/                       ← One file per feature
├── 04-flows/                          ← Mermaid sequence/state diagrams
├── 05-api/                            ← API reference by domain
├── 06-database/                       ← Schema overview + DDL reference
│   └── (SQL files)                    ← source schema + seed data
├── 07-search/                         ← Elasticsearch deep-dive
├── 08-security/                       ← Auth, JWT, RBAC, XSS
├── 09-testing/
├── 10-troubleshooting/
├── 11-deployment/
├── 12-agents/                         ← AI agent onboarding (6 files)
├── 13-decisions/
│   └── architecture-decision-records/ ← ADR-001 through ADR-004
└── _archive/                          ← Outdated docs preserved for history
```
