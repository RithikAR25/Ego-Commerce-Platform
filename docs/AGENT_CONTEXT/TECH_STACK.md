# Technology Stack — EGO Platform

This document catalogs the exact technologies, frameworks, libraries, and tools utilized across the EGO platform.

---

## 1. Backend Core Technology Stack

* **Programming Language:** Java 17 / 21
* **Framework:** Spring Boot 4.0.6
* **Security Layer:** Spring Security 7 (Stateless JWT architecture)
* **Data Access Layer:** Spring Data JPA (Hibernate ORM)
* **Build System:** Maven 3.x
* **Primary Backend Dependencies:**
  * `spring-boot-starter-web`: Exposes standard REST endpoints.
  * `spring-boot-starter-security`: Secures routes via filter chains.
  * `spring-boot-starter-data-jpa`: Relational mapping interfaces.
  * `spring-boot-starter-validation`: Jakarta validation annotations.
  * `jjwt-api` / `jjwt-impl` / `jjwt-jackson`: Cryptographic JWT signing and parsing.
  * `lombok`: Eliminates java boilerplate code.
  * `cloudinary-http44`: Cloudinary media upload integration.

---

## 2. Frontend Core Technology Stack

* **Programming Language:** TypeScript 5.x
* **Core Framework:** React 18 (Functional Component Architecture)
* **Build Engine:** Vite 5.x (Fast HMR compilation)
* **UI styling Library:** Material-UI (MUI) v5
* **State Management Store:** Zustand (Synchronous in-memory store)
* **Server Request Cache:** TanStack Query (React Query) v5 (Asynchronous sync layer)
* **Routing Engine:** React Router Dom v6
* **HTTP Client Connection:** Axios 1.x (Customized client with queued response filters)
* **Schema Validation:** Zod (Runs form check rules client-side)

---

## 3. Database, Caching & Search Engine

* **System of Record Database:** MySQL 8 (Transactional engine, InnoDB, character set `utf8mb4`)
* **High-Speed Cache & Lock Layer:** Redis (Handles session revocations, shopping carts, and concurrent checkout locks)
* **Read-Optimized Search Engine:** Elasticsearch 8.13.0 (Handles instant storefront faceted searches and autocomplete indices)

---

## 4. Third-Party Core Integrations

* **Media Hosting & Delivery:** Cloudinary (CDN-optimized images, dynamic cropping, WebP auto-transcoding)
* **Payments Merchant Gateway:** Razorpay (Checkout order compilation, webhooks, refund integrations)
* **Transactional Email Engine:** SendGrid (Event-triggered async templates, dynamic HTML injections)

---

## 5. Development Infrastructure & Operations

* **Environment Isolation:** Docker & Docker Compose (Standardized MySQL, Redis, and Elasticsearch containers)
* **Static Analysis:** ESLint (TypeScript standard checks) & Prettier (Consistent code layout configurations)
* **Backend Database Strategy:** JPA `ddl-auto=update` in development. Database migrations managed via Flyway in production environments.
