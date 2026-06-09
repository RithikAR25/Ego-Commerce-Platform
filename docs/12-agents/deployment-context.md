# deployment-context.md — EGO Platform

> **Purpose:** Deployment architecture, infrastructure topology, and production checklist for AI agents.  
> **Status:** Phase 15 (Production Deployment) is PENDING. This document covers current local/dev setup and the planned production topology.

---

## 1. Current Local Development Stack

| Service | Image | Port |
|---|---|---|
| MySQL 8.0 | `mysql:8.0` | 3307 (host) → 3306 (container) |
| Redis | `redis:7-alpine` | 6379 |
| Elasticsearch | `elasticsearch:9.0.1` | 9200 |
| Spring Boot backend | Local JVM | 8080 |
| Vite frontend | Local Node.js | 5173 |

> **Critical:** Elasticsearch must be version 9.0.1. ES 8.x is incompatible with Spring Data ES 6.x / ES Java Client 9.x (Bug ES-2 — confirmed June 2026).

---

## 2. Environment Variables — Complete Reference

### Backend (set in `raw-ego/.env` or shell)

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_URL` | No | `jdbc:mysql://localhost:3307/rawego?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false` | MySQL connection URL |
| `DB_USERNAME` | No | `root` | MySQL username |
| `DB_PASSWORD` | No | `root` | MySQL password |
| `JWT_SECRET` | **Production: YES** | `54956b31...` (insecure) | 256-bit hex key — MUST override in prod |
| `REDIS_HOST` | No | `localhost` | Redis host |
| `REDIS_PORT` | No | `6379` | Redis port |
| `REDIS_PASSWORD` | No | *(empty)* | Redis password |
| `CLOUDINARY_CLOUD_NAME` | For images | `local-dev-placeholder` | Cloudinary account name |
| `CLOUDINARY_API_KEY` | For images | `local-dev-key` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | For images | `local-dev-secret` | Cloudinary API secret |
| `CLOUDINARY_ENV` | No | `dev` | Folder prefix in Cloudinary (`dev`/`prod`) |
| `SENDGRID_API_KEY` | For emails | `SG.placeholder` | SendGrid API key |
| `SENDGRID_FROM_EMAIL` | For emails | `noreply@ego.com` | Verified sender — must be authenticated in SendGrid |
| `RAZORPAY_KEY_ID` | For payments | *(none)* | Razorpay test/live key ID |
| `RAZORPAY_KEY_SECRET` | For payments | *(none)* | Razorpay key secret |
| `RAZORPAY_WEBHOOK_SECRET` | For payments | *(none)* | Webhook HMAC secret |

### Frontend (set in `raw-ego-frontend/.env`)

| Variable | Required | Default | Description |
|---|---|---|---|
| `VITE_API_BASE_URL` | No | `http://localhost:8080` | Backend API base URL |
| `VITE_RAZORPAY_KEY_ID` | For payments | — | Razorpay public key (exposed to browser) |

---

## 3. Planned Production Docker Topology (Phase 15)

```yaml
services:
  nginx:           # Reverse proxy + SSL termination
  backend:         # Spring Boot (multi-stage Docker build)
  mysql:           # MySQL 8.0 with volume
  redis:           # Redis 7 with persistence (appendonly)
  elasticsearch:   # ES 9.0.1 single-node
```

**Key production overrides:**
```properties
# application.properties — production values
spring.jpa.hibernate.ddl-auto=validate   # NEVER 'update' in production
spring.jpa.open-in-view=false            # Prevent lazy loading over HTTP thread
CLOUDINARY_ENV=prod                      # Use production Cloudinary folder
```

**Security headers (via Nginx — not yet applied):**
```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Content-Security-Policy "default-src 'self'..." always;
```

---

## 4. Production Readiness Checklist

Before deploying to production:

### Security
- [ ] Override `JWT_SECRET` with a unique 256-bit hex key
- [ ] Set real `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` (live mode, not test)
- [ ] Set real `RAZORPAY_WEBHOOK_SECRET`
- [ ] Set real `SENDGRID_API_KEY` and verify `SENDGRID_FROM_EMAIL` in SendGrid dashboard
- [ ] Set `CLOUDINARY_ENV=prod`
- [ ] Change `spring.jpa.hibernate.ddl-auto` to `validate`
- [ ] Enable `spring.jpa.open-in-view=false`
- [ ] Configure CORS `allowedOriginPatterns` for production domain only
- [ ] Apply Nginx security headers (HSTS, X-Frame-Options, CSP)

### Infrastructure
- [ ] Set up MySQL with root password changed (not `root`)
- [ ] Set up Redis with `requirepass` enabled
- [ ] Verify ES 9.0.1 is running (not 8.x)
- [ ] Set `ES_JAVA_OPTS=-Xms1g -Xmx1g` for Elasticsearch
- [ ] Configure health checks on all containers

### Application
- [ ] Run `POST /api/v1/admin/search/reindex` after initial product import
- [ ] Run smoke tests against production environment
- [ ] Verify Razorpay webhook URL is configured in Razorpay dashboard

---

## 5. Database Bootstrap Sequence

For a fresh environment:

```bash
# 1. Apply master schema
mysql -u root -pPASSWORD -h HOST -P PORT DBNAME < docs/database/schema_v2.sql

# 2. Seed categories (3-level hierarchy — 133 categories, 130 links)
mysql -u root -pPASSWORD -h HOST -P PORT DBNAME < docs/database/01_category_seed.sql

# 3. Optional: seed test products
mysql -u root -pPASSWORD -h HOST -P PORT DBNAME < docs/database/02_product_seed.sql

# 4. Trigger ES indexing (after app starts)
curl -X POST http://localhost:8080/api/v1/admin/search/reindex \
  -H "Authorization: Bearer <ADMIN_JWT>"
```

---

## 6. Monitoring Points

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Spring Boot health check |
| `GET http://ES_HOST:9200/_cluster/health` | Elasticsearch health |
| `redis-cli -h HOST -p PORT ping` | Redis connectivity |
| `GET /api/v1/search?q=test` | ES search + circuit breaker check |
| `GET /api/v1/categories` | Category tree (tests DB + app) |
| `notification_logs` table | Email delivery success/failure audit |
