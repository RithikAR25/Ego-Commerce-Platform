# project-context.md — EGO Platform

> **Purpose:** A new AI agent reading this file should understand the full project without reading any source files.  
> **Ground truth:** All facts verified against source code as of June 6, 2026.  
> **Cross-reference:** `AGENT_CONTEXT/PROJECT_OVERVIEW.md` for implementation rules.

---

## 1. Project Identity

| Property | Value |
|---|---|
| **Project Name** | EGO |
| **Business Domain** | D2C Premium Streetwear E-Commerce |
| **Architecture Pattern** | Monolith backend + SPA frontend (REST + JWT) |
| **Development Status** | All 14 phases complete; Phase 15 (Production Deployment) pending |
| **Last QA Cycle** | June 6, 2026 — all bugs resolved, production-ready |

**Business purpose:** EGO is a premium fashion retail platform. It supports the full D2C commerce lifecycle: browse products by category, search with facets, add to cart, checkout with Razorpay payment, receive email notifications, manage orders, and submit returns. Admin users manage products, categories, orders, inventory, and users via a dedicated portal.

---

## 2. Repository Structure

```
EGO_E-commerce/
├── raw-ego/                     ← Spring Boot backend
│   ├── src/main/java/com/ego/raw_ego/
│   │   ├── address/             ← User address book
│   │   ├── admin/               ← Admin dashboard KPI endpoints
│   │   ├── auth/                ← JWT auth, user accounts, refresh tokens
│   │   ├── cart/                ← Redis-backed cart + inventory holds
│   │   ├── catalog/             ← Products, variants, categories, images
│   │   ├── common/              ← ApiResponse, exceptions, HtmlSanitizer, etc.
│   │   ├── coupon/              ← Coupon codes + checkout integration
│   │   ├── notification/        ← SendGrid async email notifications
│   │   ├── order/               ← Order lifecycle, status machine, snapshots
│   │   ├── payment/             ← Razorpay order creation + webhook
│   │   ├── returns/             ← Return requests + Razorpay refunds
│   │   ├── review/              ← Purchase-gated product reviews
│   │   ├── search/              ← Elasticsearch outbox, indexing, search
│   │   └── wishlist/            ← Variant-level wishlist
│   └── src/main/resources/
│       ├── application.properties
│       ├── elasticsearch/       ← ES index settings + mappings JSON
│       └── templates/           ← (reserved)
│
├── raw-ego-frontend/            ← Vite + React 19 + TypeScript frontend
│   └── src/
│       ├── api/                 ← All Axios API functions by domain
│       ├── components/          ← Shared UI components (layout, ui)
│       ├── features/            ← Feature-sliced modules
│       │   ├── auth/            ← Login, Register, VerifyEmail, ForgotPassword
│       │   ├── catalog/         ← Admin + storefront product/category pages
│       │   ├── checkout/        ← Checkout, PaymentVerification, OrderSuccess
│       │   ├── coupons/         ← Admin coupons management
│       │   ├── dashboard/       ← Admin dashboard
│       │   ├── navigation/      ← MegaMenu, breadcrumbs
│       │   ├── orders/          ← Customer orders + admin orders
│       │   ├── returns/         ← Customer + admin returns
│       │   ├── reviews/         ← Product reviews
│       │   ├── search/          ← Search results, filters
│       │   ├── users/           ← Admin user management
│       │   └── wishlist/        ← Wishlist page
│       ├── hooks/               ← Shared custom hooks
│       ├── pages/               ← Top-level pages (Home, Account, 404)
│       ├── providers/           ← AppProviders (QueryClient, ThemeProvider)
│       ├── router/              ← React Router v7 config + guards
│       ├── schemas/             ← Zod validation schemas
│       ├── store/               ← Zustand stores (authStore, cartStore)
│       ├── theme/               ← MUI theme config
│       ├── types/               ← TypeScript interfaces by domain
│       └── utils/               ← cloudinary URL builders, etc.
│
├── docs/                        ← Project documentation (this system)
├── tests/                       ← Node.js integration test scripts
├── AGENT_CONTEXT/               ← AI agent context files (implementation rules)
└── .env.example                 ← Environment variable template
```

---

## 3. Tech Stack (Source-Verified)

### Backend (`raw-ego/`)
| Layer | Technology | Version (pom.xml verified) |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.6 |
| Security | Spring Security | 7.x (bundled with Boot 4) |
| ORM | Spring Data JPA / Hibernate | Latest compatible |
| Database | MySQL | 8.0 (Docker port 3307) |
| Cache | Redis (Lettuce driver) | 7.x (Docker) |
| Search | Elasticsearch (Spring Data ES) | 9.0.1 (Docker) |
| JWT | JJWT | 0.12.3 |
| Image CDN | Cloudinary Java SDK (`cloudinary-http5`) | 2.3.2 |
| Email | SendGrid Java SDK | 4.10.2 |
| Payments | Razorpay Java SDK | 1.4.5 |
| API Docs | Springdoc OpenAPI | Latest Boot 4 compatible |
| Build | Maven | 3.x |

### Frontend (`raw-ego-frontend/`)
| Layer | Technology | Version (package.json verified) |
|---|---|---|
| Language | TypeScript | ~6.0.2 |
| Framework | React | ^19.2.6 |
| Build tool | Vite | ^8.0.12 |
| UI library | Material UI (MUI) | ^9.0.1 |
| Styling | Emotion | ^11.14.0 |
| Routing | React Router DOM | ^7.15.1 |
| Server state | TanStack Query | ^5.100.11 |
| Client state | Zustand | ^5.0.13 |
| HTTP client | Axios | ^1.16.1 |
| Forms | React Hook Form | ^7.76.0 |
| Validation | Zod | ^4.4.3 |
| Animations | Framer Motion | ^12.40.0 |
| Carousel | Embla Carousel | ^8.6.0 |
| Toast | Sonner | ^2.0.7 |
| SEO | React Helmet Async | ^3.0.0 |
| Progress | NProgress | ^0.2.0 |
| Charts | Chart.js + react-chartjs-2 | ^4.5.1 |

---

## 4. How to Run Locally

### Prerequisites
- Java 21 JDK
- Node.js 20+
- Docker Desktop running

### Step 1 — Start infrastructure
```bash
# From project root — start MySQL, Redis, Elasticsearch
docker run -d --name ego-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=rawego -p 3307:3306 mysql:8.0
docker run -d --name ego-redis -p 6379:6379 redis:7-alpine
docker run -d --name ego-elasticsearch -e "discovery.type=single-node" -e "xpack.security.enabled=false" -p 9200:9200 docker.elastic.co/elasticsearch/elasticsearch:9.0.1
```

### Step 2 — Configure backend environment
```bash
# Copy .env.example → raw-ego/.env (or set these in your shell)
# Required: CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET
# Required: RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET
# Required: SENDGRID_API_KEY, SENDGRID_FROM_EMAIL
# Optional (have defaults): JWT_SECRET, DB_URL, REDIS_HOST, REDIS_PORT
```

### Step 3 — Start backend
```bash
cd raw-ego
./mvnw spring-boot:run
# API available at: http://localhost:8080
# Swagger UI at:   http://localhost:8080/docs
```

### Step 4 — Seed database
```bash
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < docs/database/schema_v2.sql
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < docs/database/01_category_seed.sql
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < docs/database/02_product_seed.sql
```

### Step 5 — Start frontend
```bash
cd raw-ego-frontend
npm install
npm run dev
# Available at: http://localhost:5173
```

---

## 5. Key Environment Variables

### Backend (application.properties / .env)

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3307/rawego?...` | MySQL connection URL |
| `DB_USERNAME` | `root` | MySQL user |
| `DB_PASSWORD` | `root` | MySQL password |
| `JWT_SECRET` | `54956b31...` (256-bit hex) | **MUST override in production** |
| `spring.jwt.access-token-expiry-ms` | `900000` | Access token TTL = 15 minutes |
| `spring.jwt.refresh-token-expiry-days` | `30` | Refresh token TTL = 30 days |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(empty)* | Redis password (optional) |
| `CLOUDINARY_CLOUD_NAME` | `local-dev-placeholder` | Cloudinary account |
| `CLOUDINARY_API_KEY` | `local-dev-key` | Cloudinary key |
| `CLOUDINARY_API_SECRET` | `local-dev-secret` | Cloudinary secret |
| `CLOUDINARY_ENV` | `dev` | Folder prefix (`dev` or `prod`) |
| `SENDGRID_API_KEY` | `SG.placeholder` | SendGrid API key |
| `SENDGRID_FROM_EMAIL` | `noreply@ego.com` | Verified sender identity |
| `RAZORPAY_KEY_ID` | *(none — must set)* | Razorpay key ID |
| `RAZORPAY_KEY_SECRET` | *(none — must set)* | Razorpay key secret |
| `RAZORPAY_WEBHOOK_SECRET` | *(none — must set)* | Webhook HMAC secret |

> **Note:** App boots without Cloudinary/Razorpay/SendGrid env vars — fallback defaults allow local dev without external services. Only image upload, payment, and email features will fail.

### Frontend (`raw-ego-frontend/.env`)

| Variable | Description |
|---|---|
| `VITE_API_BASE_URL` | Backend URL (default: `http://localhost:8080`) |
| `VITE_RAZORPAY_KEY_ID` | Razorpay public key for Checkout.js |

---

## 6. Current Feature Status

All backend and frontend features are implemented and E2E tested (June 2026):

| Feature | Backend | Frontend | Status |
|---|---|---|---|
| Auth (JWT + refresh tokens) | ✅ | ✅ | Production ready |
| Category navigation (3-level) | ✅ | ✅ | Production ready |
| Product catalog (EAV variants) | ✅ | ✅ | Production ready |
| Image upload (Cloudinary) | ✅ | ✅ | Production ready |
| Elasticsearch search | ✅ | ✅ | Production ready |
| Redis cart + inventory holds | ✅ | ✅ | Production ready |
| Checkout + coupons | ✅ | ✅ | Production ready |
| Razorpay payments | ✅ | ✅ | Production ready |
| Order lifecycle | ✅ | ✅ | Production ready |
| SendGrid notifications | ✅ | ✅ | Production ready |
| Reviews (purchase-gated) | ✅ | ✅ | Production ready |
| Wishlist | ✅ | ✅ | Production ready |
| Returns + Razorpay refunds | ✅ | ✅ | Production ready |
| Coupons | ✅ | ✅ | Production ready |
| Address book | ✅ | ✅ | Production ready |
| Admin portal | ✅ | ✅ | Production ready |
| Production deployment | ❌ | ❌ | Pending (Phase 15) |
