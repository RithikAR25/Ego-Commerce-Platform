# Free Deployment Architecture — EGO Portfolio Project

> **Goal:** Keep the full EGO platform publicly accessible at zero cost, suitable for sharing with recruiters and interviewers.

---

## 1. Stack Compatibility Verdict

| Component | Free Tier Viability | Verdict |
| :--- | :--- | :--- |
| **React + Vite (Frontend)** | Excellent — static files, trivial to host | ✅ Easy |
| **Spring Boot (Backend)** | Possible with sleep workaround | ⚠️ Manageable |
| **MySQL 8** | Yes — managed free tiers exist | ✅ Easy |
| **Redis** | Yes — Upstash free tier is excellent | ✅ Easy |
| **Elasticsearch 9** | **No. Requires 1–2GB RAM. No viable free managed host.** | ❌ Must Replace |

---

## 2. Component-by-Component Evaluation

### 2A. Frontend — React 19 + Vite

| Platform | Free Tier | Sleep? | Best For |
| :--- | :--- | :--- | :--- |
| **Vercel** | 100 GB bandwidth/mo, unlimited deploys | ❌ Never sleeps | ⭐ Best choice |
| **Netlify** | 100 GB bandwidth/mo, 300 build min/mo | ❌ Never sleeps | Solid alternative |
| **GitHub Pages** | Unlimited static hosting | ❌ Never sleeps | No build pipeline |

**Recommendation: Vercel**
- Zero configuration for Vite SPA deploys.
- Connects directly to your GitHub repo — every push to `main` auto-deploys.
- Global CDN, HTTPS automatic, custom domain supported.
- Never sleeps. A recruiter visiting your link at 2am gets instant load.

---

### 2B. Backend — Spring Boot 4 (Java 21)

| Platform | RAM | Sleep | Cold Start | Credit Card? |
| :--- | :--- | :--- | :--- | :--- |
| **Render.com** | 512 MB | 15 min inactivity | ~60–90 sec (JVM) | No |
| **Koyeb** | 512 MB | 1 hour inactivity | ~45–60 sec (JVM) | No |
| **Railway** | 512 MB | No sleep | None | Yes (required) |
| **Fly.io** | 256 MB | Scale-to-zero | ~30 sec | Yes (required) |

**Recommendation: Render.com**
- No credit card required.
- 750 free instance hours per month (enough for one service running 24/7).
- Spring Boot 512MB is tight — disable `spring-boot-devtools` in production profile and tune JVM heap: `-Xmx384m`.
- **Sleep workaround:** Use [UptimeRobot](https://uptimerobot.com) (free) to ping your `/actuator/health` endpoint every 14 minutes. This prevents the 15-min sleep timer from triggering. A recruiter will never see a cold start.

> [!IMPORTANT]
> You MUST add `spring-boot-starter-actuator` and expose the `/actuator/health` endpoint publicly so UptimeRobot can ping it without authentication. This is the single most important step to making a free Spring Boot deployment portfolio-ready.

---

### 2C. MySQL Database

| Platform | Free Storage | Sleep? | MySQL Compatible? | Credit Card? |
| :--- | :--- | :--- | :--- | :--- |
| **TiDB Cloud Starter** | 5 GB row storage | No | ✅ MySQL 5.7/8.0 protocol | No |
| **Aiven Free** | 5 GB | No | ✅ Real MySQL | No |
| **PlanetScale** | ~~Free tier removed 2024~~ | — | ✅ | Yes |
| **Render PostgreSQL** | 1 GB | 90 days inactive → deleted | ❌ Postgres only | No |

**Recommendation: TiDB Cloud Starter (formerly TiDB Serverless)**
- Fully MySQL-protocol compatible — your Spring Boot JDBC connection string works unchanged.
- 5 GB storage, 50M Request Units/month — more than enough for a portfolio project.
- Never sleeps — it's serverless, autoscaling.
- No credit card required.
- Connection string format: `jdbc:mysql://<host>:4000/rawego?sslMode=VERIFY_IDENTITY`

---

### 2D. Redis ← No problem here

| Platform | Free Tier | Sleep? | Credit Card? |
| :--- | :--- | :--- | :--- |
| **Upstash Redis** | 500K commands/mo, 256 MB, 10 GB bandwidth | ❌ Never | No |
| **Redis Cloud (Redislabs)** | 30 MB, shared | ❌ Never | No |

**Recommendation: Upstash Redis**
- Serverless Redis — no connection pool to manage, HTTP-compatible.
- 500K commands/month is generous for a portfolio project.
- Supports standard `REDIS_URL` connection string — Spring Data Redis works unchanged.
- No credit card required.

---

### 2E. ⚠️ Elasticsearch — The Critical Problem

**Elasticsearch 9 requires a minimum of 1–2GB RAM** and cannot realistically run on any free tier:
- Elastic Cloud Free Trial: expires after 14 days.
- Bonsai: discontinued generous free tier.
- Render/Koyeb free tier: 512 MB — Elasticsearch will crash at startup (OOM).
- Oracle Cloud Always Free: technically capable (4 ARM cores + 24GB RAM) but requires credit card for identity verification and is complex to configure.

**This is a hard blocker for free hosting.** You have three options:

---

## 3. Elasticsearch Strategy Options

### Option A ⭐ (Recommended): Activate the Existing MySQL Fallback

Your backend **already has a circuit breaker fallback to MySQL** in `SearchService`. In production, simply set `ES_HOST` to an invalid URL (or remove it). The circuit breaker will permanently activate fallback mode, and all search requests will hit MySQL.

**Changes required:**
- Set env var `ES_HOST=http://disabled:9200` in Render.
- The fallback banner (`fallbackMode: true`) already tells the frontend to display a degraded-mode notice — you can suppress this in the frontend for portfolio use.
- MySQL full-text search still works for basic queries.

**Portfolio value:** A recruiter sees a working search. You explain in the README that Elasticsearch runs locally in development and degrades gracefully to MySQL in free-tier production. This demonstrates **professional engineering judgment**, not a limitation.

---

### Option B: Replace Elasticsearch with Meilisearch

[Meilisearch](https://www.meilisearch.com) is a modern, lightweight, open-source search engine:
- 10× less memory than Elasticsearch (~100 MB vs 1+ GB).
- Can run on Render's free tier (512 MB).
- Instant search, faceted filtering, typo tolerance — same features you built with ES.
- Has a **Meilisearch Cloud free tier** (1 index, 100K documents, no CC).
- API-compatible migration: replace `SearchService.java` with a Meilisearch client.

**This is the best option if you want live search in production** and are willing to spend 1–2 days rewriting the search service.

---

### Option C: Keep Elasticsearch — Run on Oracle Cloud Always Free

Oracle Cloud Always Free gives you **4 ARM CPU cores + 24 GB RAM** — enough to run the entire stack on one VM including Elasticsearch.

- No time limit (truly always free, not a trial).
- Requires credit card for identity verification at signup (not charged).
- Significant setup effort: install Docker, configure firewall, reverse proxy, SSL.
- Best for a developer who wants full control and a production-grade environment.

---

## 4. ⭐ Recommended Free Architecture (Zero Cost)

```
┌─────────────────────────────────────────────────────────────────┐
│                    RECRUITER'S BROWSER                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTPS
                           ▼
┌──────────────────────────────────────┐
│           Vercel (Frontend)          │
│   React 19 + Vite SPA               │
│   Free forever, no sleep, CDN       │
│   Custom domain supported           │
└──────────────────────────────────────┘
                           │ REST API calls to /api/*
                           ▼
┌──────────────────────────────────────┐
│        Render.com (Backend)          │
│   Spring Boot 4 · Java 21           │
│   512 MB RAM · -Xmx384m             │
│   Sleep: prevented by UptimeRobot   │
│   750 hrs/month free                │
└────────────┬────────────┬────────────┘
             │            │
             ▼            ▼
┌────────────────┐  ┌─────────────────┐
│  TiDB Cloud    │  │  Upstash Redis  │
│  Starter       │  │  Free Tier      │
│  MySQL proto   │  │  500K cmd/mo    │
│  5 GB storage  │  │  256 MB         │
│  Never sleeps  │  │  Never sleeps   │
└────────────────┘  └─────────────────┘

External (already free):
  ✅ Cloudinary  — image CDN (already integrated)
  ✅ Razorpay    — payments (test mode, no charge)
  ✅ SendGrid    — transactional email (100/day free)

Search:
  ✅ MySQL fallback mode (circuit breaker already built)
  OR
  ✅ Meilisearch Cloud free tier (if you want live search)
```

---

## 5. Free Tier Limits Summary

| Service | What's Free | Risk of Hitting Limit |
| :--- | :--- | :--- |
| Vercel | 100 GB/month bandwidth | Very low for portfolio |
| Render.com | 750 instance-hours/month | None if 1 service + UptimeRobot |
| TiDB Cloud Starter | 5 GB storage, 50M RU/month | Very low for portfolio |
| Upstash Redis | 500K commands/month | Low — carts + refresh tokens only |
| Cloudinary | 25 credits/month | Already using, within limits |
| SendGrid | 100 emails/day | Within limits for portfolio |
| UptimeRobot | 50 monitors, 5-min interval | More than enough |

---

## 6. What to Tell Recruiters

In your README, include a section like this:

> **Production Architecture Note**
>
> This project is deployed on a fully free cloud stack (Vercel + Render + TiDB + Upstash).
> Elasticsearch, which powers faceted product search in local development, is replaced by MySQL full-text search in the free-tier deployment due to Elasticsearch's 1GB+ RAM requirement. The backend's circuit breaker pattern gracefully degrades search to MySQL, demonstrating production-awareness of resource constraints.

This turns a limitation into a feature that showcases your engineering maturity.

---

## 7. Implementation Steps Summary

1. **Add Spring Boot Actuator** → expose `/actuator/health` (no auth)
2. **Create production Spring profile** (`application-prod.properties`) → tune JVM, disable devtools, point to TiDB + Upstash URLs
3. **Create `Dockerfile`** for the Spring Boot JAR
4. **Create `Dockerfile`** for the frontend (Nginx static serve OR just deploy via Vercel CLI)
5. **Provision Render.com** → deploy backend Docker image, inject env vars
6. **Provision Vercel** → connect GitHub repo, set `VITE_API_BASE_URL` to Render URL
7. **Provision TiDB Cloud Starter** → get connection string, inject into Render env
8. **Provision Upstash Redis** → get `REDIS_URL`, inject into Render env
9. **Register UptimeRobot** → ping Render backend every 14 minutes
10. **Update CORS** in `SecurityConfig.java` → allow only your Vercel domain
11. **Generate new `JWT_SECRET`** → 256-bit hex, inject into Render env (never use dev default)
