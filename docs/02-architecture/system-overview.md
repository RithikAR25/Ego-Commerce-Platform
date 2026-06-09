# System Overview — EGO Platform Architecture

> **Source-verified:** June 6, 2026. All component versions verified from `pom.xml` and `package.json`.

---

## High-Level Architecture

```mermaid
flowchart TB
    Browser["Browser\n(React 19 + Vite 8)"]
    
    subgraph Backend["Spring Boot 4.0.6 · Java 21 · Port 8080"]
        direction TB
        JWTFilter["JwtAuthenticationFilter"]
        Controllers["Controllers (14 modules)"]
        Services["Services"]
        Repos["Repositories (JPA)"]
    end

    subgraph DataStores["Data Stores"]
        MySQL["MySQL 8\nPort 3307\n(primary store)"]
        Redis["Redis 7\nPort 6379\n(cart + sessions)"]
        ES["Elasticsearch 9.0.1\nPort 9200\n(search index)"]
    end

    subgraph External["External Services"]
        Cloudinary["Cloudinary CDN\n(image upload + serve)"]
        Razorpay["Razorpay\n(payments + refunds)"]
        SendGrid["SendGrid\n(transactional email)"]
    end

    Browser <-->|"REST + JWT"| JWTFilter
    JWTFilter --> Controllers
    Controllers --> Services
    Services --> Repos
    Repos --> MySQL
    Services --> Redis
    Services --> ES
    Services -->|"image upload (outside @Txn)"| Cloudinary
    Services -->|"payment order (outside @Txn)"| Razorpay
    Razorpay -->|"webhook → HMAC verified"| Controllers
    Services -->|"@Async @EventListener"| SendGrid
    Browser -->|"Checkout.js modal"| Razorpay
    Browser -->|"Cloudinary transformation URLs"| Cloudinary
```

---

## Component Versions (Source-Verified)

| Component | Version | Source |
|---|---|---|
| Java | 21 | `pom.xml` `<java.version>` |
| Spring Boot | 4.0.6 | `pom.xml` `<parent>` |
| Spring Security | 7.x (bundled) | Spring Boot BOM |
| JJWT | 0.12.3 | `pom.xml` |
| Cloudinary SDK | 2.3.2 (`cloudinary-http5`) | `pom.xml` |
| SendGrid SDK | 4.10.2 | `pom.xml` |
| Razorpay SDK | 1.4.5 | `pom.xml` |
| Springdoc OpenAPI | Boot 4 compatible | `pom.xml` |
| React | 19.2.6 | `package.json` |
| TypeScript | ~6.0.2 | `package.json` |
| Vite | 8.0.12 | `package.json` |
| MUI (Material UI) | 9.0.1 | `package.json` |
| React Router DOM | 7.15.1 | `package.json` |
| TanStack Query | 5.100.11 | `package.json` |
| Zustand | 5.0.13 | `package.json` |
| Axios | 1.16.1 | `package.json` |
| Zod | 4.4.3 | `package.json` |
| MySQL | 8.0 | Docker |
| Redis | 7-alpine | Docker |
| Elasticsearch | 9.0.1 | Docker |

---

## Module Map

### Backend — 14 Feature Modules

```mermaid
graph TD
    common["common\n(ApiResponse, exceptions,\nHtmlSanitizer, SlugUtils)"]
    auth["auth\n(JWT, refresh tokens, RBAC)"]
    catalog["catalog\n(products, categories,\nvariants, EAV, images)"]
    cart["cart\n(Redis HASH, inventory holds)"]
    order["order\n(8-state machine, snapshots)"]
    payment["payment\n(Razorpay create + webhook)"]
    returns["returns\n(5-state machine, Razorpay refund)"]
    review["review\n(purchase-gated)"]
    wishlist["wishlist\n(variant-level, MySQL)"]
    coupon["coupon\n(FLAT/PERCENTAGE, checkout integration)"]
    notification["notification\n(5 async SendGrid events)"]
    search["search\n(ES 9.0.1, outbox pattern)"]
    address["address\n(address book, XSS-safe)"]
    admin["admin\n(KPI dashboard metrics)"]

    common --> auth
    common --> catalog
    common --> cart
    common --> order
    common --> payment
    common --> returns
    common --> review
    common --> wishlist
    common --> coupon
    common --> notification
    common --> search
    common --> address
    common --> admin
```

### Frontend — Feature-Sliced

```mermaid
graph LR
    subgraph State
        ZustandAuth["authStore\n(AT + user)"]
        ZustandCart["cartStore\n(badge + sessionId)"]
        TQ["TanStack Query\n(all server state)"]
    end

    subgraph Features
        AuthFeat["features/auth"]
        CatalogFeat["features/catalog\n(storefront + admin)"]
        CheckoutFeat["features/checkout"]
        OrdersFeat["features/orders\n(storefront + admin)"]
        ReturnsFeat["features/returns\n(storefront + admin)"]
        OtherFeat["features/\n(search, wishlist,\nreviews, coupons, users)"]
    end

    subgraph Router
        RootL["RootLayout\n(Navbar + Footer)"]
        CheckoutL["CheckoutLayout\n(header only)"]
        AdminL["AdminLayout\n(sidebar)"]
    end

    ZustandAuth --> Features
    ZustandCart --> Features
    TQ --> Features
    Features --> Router
```

---

## Request Lifecycle

```mermaid
sequenceDiagram
    participant B as Browser
    participant N as Nginx (prod only)
    participant F as JwtAuthenticationFilter
    participant A as AuthorizationFilter
    participant C as Controller
    participant S as Service
    participant DB as MySQL/Redis/ES

    B->>N: HTTPS request
    N->>F: HTTP proxy
    F->>F: Parse Bearer token
    F->>F: Validate HMAC signature, expiry
    F->>DB: Load user by email (cache optional)
    F->>F: Check passwordChangedAt > issuedAt
    F->>A: Set SecurityContext
    A->>A: Evaluate @PreAuthorize / hasRole
    A->>C: Route to controller
    C->>S: Call service method
    S->>DB: Read/write
    S-->>C: Return domain object
    C-->>B: ApiResponse<T> envelope
```

---

## Data Flow Summary

| Operation | Flow |
|---|---|
| **Search** | Browser → `GET /search` → SearchService → ES (fallback: MySQL) |
| **Add to cart** | Browser → `POST /cart/add` → CartService (Redis HSET) + InventoryReservationService (MySQL optimistic lock) |
| **Checkout** | Browser → `POST /orders/checkout` → `@Transactional` {inventory commit + order persist + cart clear} → async email |
| **Payment** | Browser → Razorpay modal → Razorpay webhook → Backend HMAC verify → order CONFIRMED |
| **Product update** | Admin → `PUT /admin/products/{id}` → ProductService → MySQL + search_outbox → OutboxPoller (every 5s) → ES bulk index |
| **Return** | Customer → `POST /orders/{id}/returns` → ReturnService {window check} → Admin review → Razorpay refund API (outside @Txn) → inventory restore |

---

## Port Reference

| Service | Port |
|---|---|
| Backend (Spring Boot) | `8080` |
| Frontend (Vite dev server) | `5173` |
| MySQL | `3307` (host) → `3306` (container) |
| Redis | `6379` |
| Elasticsearch | `9200` |
| Swagger UI | `http://localhost:8080/docs` |

---

## Security Boundary

```
PUBLIC (no auth):          /api/v1/auth/register, /login, /refresh
                           /api/v1/categories/**
                           /api/v1/products/** (GET only)
                           /api/v1/search, /search/autocomplete
                           /api/v1/coupons/validate
                           /api/v1/webhooks/razorpay  (HMAC-secured)
                           /api/v1/*/reviews (GET only)
                           /docs/**

AUTHENTICATED (any role):  /api/v1/cart/**
                           /api/v1/orders/**
                           /api/v1/addresses/**
                           /api/v1/wishlist/**
                           /api/v1/payments/**
                           /api/v1/*/reviews (POST)
                           /api/v1/auth/logout, /me

ROLE_ADMIN only:           /api/v1/admin/**
```
