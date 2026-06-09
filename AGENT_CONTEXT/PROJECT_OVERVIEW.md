# Project Overview — EGO E-Commerce Platform

This document serves as the canonical onboarding overview for the EGO E-commerce codebase. EGO is a premium streetwear fashion brand platform designed for high performance, visual excellence, and modern stateless architecture.

---

## 1. Project Purpose & Business Domain
EGO is a direct-to-consumer (D2C) streetwear e-commerce platform. Streetwear retail demands a highly interactive storefront, instant search capability, a robust and fast checkout flow, and strict inventory consistency to handle flash drops (high-concurrency purchase spikes).

### Core Business Pillars
* **Curated Visual Showcase:** Seamless, responsive product detail pages with dynamic variant attribute selection (Color × Size) and on-the-fly CDN-optimized media loading.
* **Strict Inventory Integrity:** Guaranteed stock safety (no double-sells) via fine-grained Redis locks and MySQL optimistic locking.
* **Stateless Authenticated Flows:** Seamless user experience backed by cryptographic JWT tokens with advanced security features (Token Rotation, Family Revocation).
* **Faceted Search Experience:** Instant, read-optimized product searching and filtering based on Elasticsearch.

---

## 2. Architecture Summary

```
                  ┌──────────────────────────────────────────────┐
                  │                 Vite React                   │
                  │   (Storefront + Admin, Zustand, React Query) │
                  └──────────────────────┬───────────────────────┘
                                         │
                                         │ REST + Stateless JWT
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                            Spring Boot API Gateway                             │
│                                                                                │
│  ┌─────────────────────────┐  ┌─────────────────────────┐  ┌────────────────┐  │
│  │   Auth Module (Phase 2) │  │ Catalog Module (Phase 3)│  │   Common Layer │  │
│  └────────────┬────────────┘  └────────────┬────────────┘  └────────┬───────┘  │
└───────────────┼────────────────────────────┼────────────────────────┼──────────┘
                │                            │                        │
        ┌───────┼────────────────────────────┼──────────┐             │
        ▼       ▼                            ▼          ▼             ▼
  ┌───────────┐ ┌───────────┐          ┌───────────┐ ┌───────────┐ ┌──────────┐
  │   Redis   │ │   MySQL   │          │   Cloudi- │ │  Elastic  │ │ Razorpay │
  │ (RT/Cart) │ │ (Store)   │          │   nary    │ │  search   │ │ SendGrid │
  └───────────┘ └───────────┘          └───────────┘ └───────────┘ └──────────┘
```

The system employs a decoupled client-server architecture:
* **Frontend:** A single-page application (SPA) built with React, TypeScript, Vite, and Material-UI (MUI). Client-side state is handled by Zustand, server-cache by TanStack Query, and client-side routing by React Router.
* **Backend:** A monolithic REST API built on Spring Boot 3.x/4.x (Java 17/21). The backend is structured into clear feature modules (e.g., `auth`, `catalog`) to maintain boundaries and pave the way for potential microservices transition.
* **Database & Caching:** MySQL 8 is the primary transactional system of record. Redis is utilized for high-speed cart caching, JWT session revocation, and checkout locking. Elasticsearch handles read-optimized faceted product search.
* **External Integrations:** Cloudinary for media storage, Razorpay for payments, and SendGrid for transactional emails.

---

## 3. Backend Overview
The backend repository (`raw-ego`) is built on **Spring Boot 4.0.6 (Spring Security 7)**.
* **Statelessness:** Session creation is disabled (`SessionCreationPolicy.STATELESS`). Cryptographic signatures protect authentication, with rotation performed silently to keep users logged in.
* **Clean Code Structure:** Controllers handle input validation (`@Valid`) and map parameters; Services execute transaction boundaries (`@Transactional`) and enforce domain rules; Repositories perform data queries via Spring Data JPA.
* **Error Handling:** Standardized error response envelope (`ApiResponse<T>`) returned globally by `@RestControllerAdvice`.

---

## 4. Frontend Overview
The frontend repository (`raw-ego-frontend`) is a modern React TypeScript application driven by Vite.
* **MUI Theme System:** Curved surfaces, dark modes, Harmonious HSL color palettes, and standard typographic systems (Google Font: Outfit/Inter).
* **Zustand State Store:** Separate, lightweight stores manage localized states (`authStore`, `uiStore`, `cartStore`).
* **Axios Pipeline:** Standardized HTTP client with queued silent refresh. Concurrent 401 interceptors handle refresh token rotation atomically, preventing double-use family revocation flags from firing.

---

## 5. Major Integrations
* **Cloudinary:** Direct-to-bucket administrative uploads. Backend receives images as MultipartFiles, signs and uploads via SDK, and registers unique `public_id` values. Sizes are compiled on-the-fly.
* **Razorpay:** Payment capture verified through SHA-256 HMAC webhook signatures. Real-time Redis inventory holds are created immediately before order generation to guarantee fulfillment.
* **SendGrid:** Event-driven async transactional emails. Notification records are logged to `notification_log` for reliability and audit trails.
* **Elasticsearch:** Read-optimized faceted catalog search running in tandem with the primary relational database.

---

## 6. Locked Architecture Decisions

### SKU Design
Format: `EGO-{CATEGORY_CODE}-{PRODUCT_CODE}-{COLOR_CODE}-{SIZE}` (e.g., `EGO-MEN-0001-BLK-M`). Server-side generated, completely unique, and immutable.

### Category Structure
Enforced flat 2-level tree structure (Depth = 1). Root category (e.g., "Men", `parent_id = NULL`) references a Subcategory (e.g., "Oversized Tees"). No recursive trees allowed.

### Pricing Model
Per-variant 3-tier decimals: `price` (storefront), `compare_at_price` (crossed-out original), and `cost_price` (internal COGS, strictly admin-only).

### Inventory Control
1:1 variant-to-inventory mapping. Relies on MySQL optimistic locking (`version` column) and Redis `SETNX` locks during payment checkout windows.

### URL Slugs
SEO-friendly URL slugs (e.g., `/products/oversized-acid-wash-tee`) generated server-side. Public storefront routes always use slugs, not primary database IDs.

---

## 7. Important Business Flows

### User Signup & Login Flow
```
User Registers → BCrypt Salt/Hash (Strength 12) → Saved in MySQL
User Logs In → Credentials Checked → Issues AT (15 min) + RT (30 days)
AT sent as Bearer Header. RT hashed using SHA-256 and stored in database.
```

### The Silent Refresh Loop
```
Frontend calls GET /auth/me → AT Expired → Backend returns 401
Frontend interceptor catches 401 → Locks calls → Requests POST /auth/refresh
Backend rotates RT (generates new AT + RT, invalidates old RT)
Frontend updates memory + retry queued requests with new AT
```

### Variant Creation & Inventory Binding
```
Admin specifies attributes (Color + Size IDs) & price/stock
Backend compiles SKU dynamically: EGO-MEN-0001-BLK-M
Builds variant + builds inventory and sets bidirectional reference
Saves variant → CascadeType.ALL inserts inventory atomically
```
