# Backend Context & Architecture — EGO Platform

This document describes the backend systems, configurations, design patterns, and processing flows implemented in the EGO backend (`raw-ego`).

---

## 1. Technological Architecture

The EGO backend is developed on **Java 17/21** and **Spring Boot 4.0.6 (Spring Security 7)**.

```
           [ Client HTTP Request ]
                      │
                      ▼
            ┌───────────────────┐
            │   CorsFilter      │  ← Verifies origin headers
            └────────┬──────────┘
                     │
                     ▼
        ┌───────────────────────────┐
        │  JwtAuthenticationFilter  │  ← Resolves Bearer token, loads UserDetails,
        │                           │    verifies passwordChangedAt constraint
        └────────────┬──────────────┘
                     │
                     ▼
        ┌───────────────────────────┐
        │    AuthorizationFilter    │  ← Filters based on PUBLIC_MATCHERS or RBAC role
        └────────────┬──────────────┘
                     │
                     ▼
        ┌───────────────────────────┐
        │  Standard Controllers     │  ← Input validation (@Valid) and DTO mapping
        └────────────┬──────────────┘
                     │
                     ▼
        ┌───────────────────────────┐
        │  Feature Service Layer    │  ← Transactional boundary, business rules
        └──────┬──────────────┬─────┘
               │              │
               ▼              ▼
        ┌────────────┐  ┌────────────┐
        │ Hibernate  │  │ Secondary  │  ← Cloudinary, Redis lock,
        │ (MySQL)    │  │ Engines    │    Elasticsearch Event publishers
        └────────────┘  └────────────┘
```

---

## 2. Spring Security & JWT Architecture

EGO utilizes a stateless, token-based security configuration centered in `SecurityConfig.java`. Sessions are entirely disabled (`SessionCreationPolicy.STATELESS`).

### The JWT Authentication Filter
On protected routes, `JwtAuthenticationFilter` intercepts incoming requests:
1. **Header extraction:** Resolves the `Authorization` header and extracts the `Bearer` string.
2. **Signature verification:** Verifies the cryptographic HMAC signature against `spring.jwt.secret`.
3. **Password Changed Guard:** Extends stateless authentication. It extracts the issued-at time (`iat`) from the JWT and compares it to the user's `password_changed_at` field in the database. If the user changed their password, all previously issued JWTs are instantly rejected, prompting a clean logout.
4. **Security Context population:** Sets the authenticated user principal into `SecurityContextHolder`.

### Refresh Token Architecture (Database-Backed)
Refresh tokens are handled via **Token Rotation** and **Family Reuse Protection**:
1. **Storage:** The raw refresh token (UUID) is sent to the client. The database (`refresh_tokens` table) stores only the **SHA-256 hashed** version for security.
2. **Token Rotation:** Every refresh operation (`POST /auth/refresh`) rotates the token. The old refresh token is deleted, and a new token pair is returned to the client.
3. **Theft Detection:** The system links related sessions via a shared `family_id` UUID. If a client attempts to refresh using an already rotated (spent) refresh token:
   * The system detects an anomaly (potential token theft).
   * It immediately revokes the **entire token family** (deletes all tokens sharing the same `family_id`).
   * The legitimate user and the attacker are instantly logged out.

### Role-Based Access Control (RBAC)
EGO maps roles cleanly using Spring Security standards:
* **Roles:** Modeled inside the `UserRole` enum (`ROLE_CUSTOMER`, `ROLE_ADMIN`).
* **Route Configuration:** Defined in `SecurityConfig.PUBLIC_MATCHERS`.
  * Public routes are allowed via `permitAll()` (e.g., categories display, public product list, webhooks).
  * Administrative routes under `/api/v1/admin/**` enforce `hasRole('ADMIN')`.
  * Storefront customer endpoints default to `.anyRequest().authenticated()`.

---

## 3. Data Processing & Transaction Patterns

### Service Layer Boundaries
* Service operations must declare transactional behavior. Operations modifying data (inserts/updates/deletes) must be marked with `@Transactional`.
* Services that only fetch data must be annotated with `@Transactional(readOnly = true)` to avoid dirty-checking overhead in Hibernate.

### Cascade Operations
* **Product Variant & Inventory Cascade:** Standardized Hibernate configurations handle the variant-inventory lifecycle. In `ProductVariant.java`, Cascade is set to `CascadeType.ALL` for `inventoryRecord`.
* **Standard Save Pattern:** When creating variants, services must wire both sides of the relationship (bidirectional references) **before** executing `variantRepository.save(variant)`. This allows Hibernate to insert both entities atomically in a single transactional unit without generating transient entity exceptions.

---

## 4. Exception Mapping Architecture

All exceptions are routed through the global `@RestControllerAdvice` in `GlobalExceptionHandler.java`.

### Hierarchy Mapping

| Custom Exception | Source Exception / Scenario | Mapped HTTP Status | Standard ApiResponse Payload |
|---|---|---|---|
| `MethodArgumentNotValidException` | Fields failing `@NotNull`, `@NotBlank` etc. | `400 Bad Request` | `{ success: false, message: "Validation failed...", errors: [{field, message}] }` |
| `IllegalArgumentException` | Violated business rules (e.g., compareAtPrice <= price) | `400 Bad Request` | `{ success: false, message: ex.getMessage() }` |
| `InvalidDataAccessApiUsageException` | Bad query params or invalid Hibernate sorting fields | `400 Bad Request` | `{ success: false, message: "Invalid query parameter. Use valid fields..." }` |
| `AuthException` | Invalid login credentials or expired tokens | `401 Unauthorized` | `{ success: false, message: ex.getMessage() }` |
| `AccessDeniedException` | Valid JWT but insufficient role access | `403 Forbidden` | `{ success: false, message: "Access denied" }` |
| `ResourceNotFoundException` | Entity not found by primary keys or slugs | `404 Not Found` | `{ success: false, message: ex.getMessage() }` |
| `ConflictException` | SKU collisions or duplicated database slugs | `409 Conflict` | `{ success: false, message: ex.getMessage() }` |
| `Exception` | Unexpected internal runtime errors | `500 Server Error` | `{ success: false, message: "An unexpected error occurred..." }` (logs trace internally, hides from client) |

---

## 5. Integrations Architecture

### Cloudinary (Media Store)
* Administrators upload raw image files via Multipart requests. The backend leverages the Cloudinary Java SDK to stream the payload, saving the returned `public_id` and the secure resource URL.
* Cloudinary folders are partitioned dynamically by active environment (`ego/dev/`, `ego/prod/`) and product ID.

### Redis Caching & Locking
* **Cart Storage:** User carts are cached in Redis to offload heavy database checks during session browsing.
* **Concurrency Locking:** To prevent overselling during high-concurrency flash sales, the system acquires a Redis-based checkout lock using `SETNX` on `lock:checkout:{variantId}` with a strict 10-minute expiry during the payment window.

### Elasticsearch (Sync and Search)
* **Write Pipeline:** Data mutations are event-driven. Product modifications trigger Spring `ApplicationEvent` occurrences, which are resolved asynchronously by an `@Async` listener to execute non-blocking upsert indices.
* **Faceted Queries:** Custom search repository mappings construct native elastic search parameters, building aggregations to compile real-time filtering counts.
