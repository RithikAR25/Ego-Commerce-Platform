# EGO Backend API (`raw-ego`)

This is the Spring Boot REST API for the EGO E-Commerce platform. It serves as the stateless, centralized business logic layer for both the customer storefront and the admin portal.

---

## 🛠️ Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 4.0.6
- **Security:** Spring Security 7
- **Data Access:** Spring Data JPA / Hibernate
- **Database:** MySQL 8
- **Caching & Locks:** Redis 7
- **Search Engine:** Elasticsearch 9.0.1
- **Build Tool:** Maven 3.x

---

## 🚀 Quick Start

### Prerequisites

- Java 21 JDK
- Docker Desktop (for backing services)

### 1. Start Backing Services

The API requires MySQL, Redis, and Elasticsearch to boot successfully.
Start them via Docker:

```bash
docker run -d --name ego-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=rawego -p 3307:3306 mysql:8.0
docker run -d --name ego-redis -p 6379:6379 redis:7-alpine
docker run -d --name ego-elasticsearch -e "discovery.type=single-node" -e "xpack.security.enabled=false" -p 9200:9200 docker.elastic.co/elasticsearch/elasticsearch:9.0.1
```

### 2. Environment Configuration

Before starting the application, check whether the required environment file already exists:

- If `.env` exists, use it.
- Otherwise create `.env` from `.env.example` and populate the required values.

If environment files have already been provided, place them in the appropriate project directories and proceed to the next step. Otherwise, create them from the provided `.env.example` templates:

```bash
cp .env.example .env
```

#### Generating a JWT Secret

`JWT_SECRET` is **required** — the application will not start without it. Open your `.env` file and set it to a freshly generated 64 characters 256-bit hex key.

**Option A — Online Generator (easiest):**
Visit **[https://jwtsecrets.com/#generator](https://jwtsecrets.com/#generator)**, select **256-bit**, and copy the generated hex string directly into your `.env`.

**Option B — PowerShell (Windows):**

```powershell
[System.BitConverter]::ToString((1..32 | ForEach-Object { [byte](Get-Random -Max 256) })).Replace("-","").ToLower()
```

**Option C — Git Bash / macOS / Linux:**

```bash
openssl rand -hex 32
```

Copy the output and paste it into your `.env`:

```env
JWT_SECRET=paste_your_generated_key_here
```

> ⚠️ Never reuse the same secret across environments. Generate a fresh key for every machine.

### 3. Seed the Database

Before starting the backend, seed the initial database schema and test data:

```bash
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < ../docs/database/schema_v2.sql
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < ../docs/database/01_category_seed.sql
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < ../docs/database/02_product_seed.sql
```

### 4. Run the Application

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

---

## 📖 API Documentation (Swagger)

The backend auto-generates OpenAPI 3.0 documentation using Springdoc.
Once the server is running, visit:

**[http://localhost:8080/docs](http://localhost:8080/docs)**

From the Swagger UI, you can authenticate via the `POST /api/v1/auth/login` endpoint and easily test protected endpoints by clicking the **Authorize** lock icon.

---

## 🏗️ Project Structure

The codebase is organized by business domain (feature modules) rather than technical layers:

```
src/main/java/com/ego/raw_ego/
├── auth/          # JWT authentication, login, silent refresh
├── catalog/       # Products, categories, attributes, Cloudinary images
├── cart/          # Redis-backed shopping cart
├── checkout/      # Razorpay payment gateways, coupons
├── order/         # Order lifecycle, status history, shipping
├── search/        # Elasticsearch indexing, transactional outbox
├── notification/  # SendGrid async event listeners
├── user/          # User management, address book
└── common/        # Global exception handling, configurations
```

---

## 🔐 Architecture Highlights

- **Stateless Security:** No HTTP sessions. Relies purely on HS256 JWT tokens. Uses a dual-token (Access + Refresh) rotation strategy with immediate family revocation on refresh token theft.
- **Concurrent Inventory Locks:** When a user initiates checkout, `CartService` acquires a pessimistic `SETNX` lock in Redis for the specific variants, guaranteeing stock safety during the payment window.
- **Transactional Outbox:** To keep MySQL and Elasticsearch perfectly synchronized without distributed transactions, the catalog module writes to an `outbox` table during the same `@Transactional` boundary as the product update. A background thread safely syncs this to Elasticsearch.

For more detailed architectural patterns, see the root `docs/` folder in the repository.
