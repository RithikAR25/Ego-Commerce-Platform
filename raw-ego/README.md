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

The Docker MySQL container starts completely empty. These commands import the schema
(table definitions) and seed data into the running container.

> **Why `docker exec`?** The `mysql` command-line client is not bundled with Docker —
> it comes with a full MySQL Server install. If you only installed Docker (recommended),
> use `docker exec` to run the client *inside* the already-running container instead.
>
> Run all commands from the **project root** (`EGO_E-commerce/`):

**macOS / Linux / Git Bash (Windows):**
```bash
# 1. Import schema (all table definitions)
docker exec -i ego-mysql mysql -u root -proot rawego < docs/06-database/schema_v2.sql

# 2. Seed categories
docker exec -i ego-mysql mysql -u root -proot rawego < docs/06-database/01_category_seed.sql

# 3. Seed products
docker exec -i ego-mysql mysql -u root -proot rawego < docs/06-database/02_product_seed.sql
```

**Windows PowerShell** (the `<` redirect does not work in PowerShell — use `Get-Content` instead):
```powershell
Get-Content docs\06-database\schema_v2.sql          | docker exec -i ego-mysql mysql -u root -proot rawego
Get-Content docs\06-database\01_category_seed.sql    | docker exec -i ego-mysql mysql -u root -proot rawego
Get-Content docs\06-database\02_product_seed.sql     | docker exec -i ego-mysql mysql -u root -proot rawego
```

> **Verify seeding worked:**
> ```bash
> docker exec -it ego-mysql mysql -u root -proot rawego -e "SHOW TABLES;"
> ```
> You should see a list of table names (`users`, `products`, `categories`, `orders`...).
> If the list is empty, re-run the seed commands above.

### 4. Run the Application

**macOS / Linux / Git Bash:**
```bash
cd raw-ego
./mvnw spring-boot:run
```

**Windows — PowerShell or Command Prompt:**
```powershell
cd raw-ego
.\mvnw.cmd spring-boot:run
```

> `mvnw` / `mvnw.cmd` is the **Maven Wrapper** — a script bundled with the project that
> downloads the correct Maven version automatically. You do **not** need to install Maven.

**Alternative — Run from IntelliJ IDEA (Recommended for development):**
1. Download [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) (free).
2. Open IntelliJ → **Open** → select the `raw-ego/` folder.
3. Wait for Maven import and indexing to complete.
4. Open `RawEgoApplication.java` → click the green **Run ▶** button.
5. Install the **EnvFile** plugin (**File → Settings → Plugins → search "EnvFile"**) and point it to `raw-ego/.env` in the Run Configuration so the app reads your credentials.

The API will be available at `http://localhost:8080`.

---

## 📖 API Documentation (Swagger)

The backend auto-generates OpenAPI 3.0 documentation using Springdoc.

> **Swagger is disabled by default** to prevent API exposure in production.
> To enable it locally, add this to your `raw-ego/.env` file:
> ```env
> SWAGGER_ENABLED=true
> ```

Once the server is running with `SWAGGER_ENABLED=true`, visit:

**[http://localhost:8080/docs](http://localhost:8080/docs)**

From Swagger UI, authenticate via `POST /api/v1/auth/login` and click the **Authorize 🔒** lock icon to test protected endpoints.

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
