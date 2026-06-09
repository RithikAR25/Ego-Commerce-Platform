# Local Development Guide

## Prerequisites

Ensure your machine has the following installed:
- **Java 21 JDK**
- **Node.js 20+**
- **Docker Desktop** (or equivalent Docker engine)
- **Git**

## 1. Start Infrastructure Services

The EGO platform relies on MySQL, Redis, and Elasticsearch. You can run all three locally via Docker.

From the project root (or anywhere), run these commands:

```bash
# 1. MySQL 8 (port 3307 to avoid conflicts)
docker run -d --name ego-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=rawego -p 3307:3306 mysql:8.0

# 2. Redis 7 (port 6379)
docker run -d --name ego-redis -p 6379:6379 redis:7-alpine

# 3. Elasticsearch 9.0.1 (port 9200)
docker run -d --name ego-elasticsearch -e "discovery.type=single-node" -e "xpack.security.enabled=false" -p 9200:9200 docker.elastic.co/elasticsearch/elasticsearch:9.0.1
```

*Note on Elasticsearch version:* Spring Boot 4.x bundles ES Java Client 9.x, which **only** works with Elasticsearch 9.x. Do not use ES 8.x.

## 2. Environment Configuration

Before starting the application, check whether the required environment file already exists:

**Backend:**
* If `.env` exists, use it.
* Otherwise create `.env` from `.env.example` and populate the required values.

**Frontend:**
* If `.env.local` exists, use it.
* Otherwise create `.env.local` from `.env.example` and populate the required values.

If environment files have already been provided, place them in the appropriate project directories and proceed to the next step. Otherwise, create them from the provided `.env.example` templates:

```bash
cp raw-ego/.env.example raw-ego/.env
cp raw-ego-frontend/.env.example raw-ego-frontend/.env.local
```

**Required minimal variables:**
`JWT_SECRET` is strictly required to boot the backend. You must generate a 256-bit hex key (e.g., `openssl rand -hex 32`). Without this, Spring Boot will crash immediately.

For full local functionality, set these in your environment files:
```properties
JWT_SECRET=your_256_bit_hex_secret

CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

RAZORPAY_KEY_ID=rzp_test_yourkey
RAZORPAY_KEY_SECRET=your_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

SENDGRID_API_KEY=SG.your_sendgrid_key
SENDGRID_FROM_EMAIL=your_verified_sender@domain.com
```

## 3. Seed the Database

Before starting the backend, seed the initial database schema and test data.
Run these commands from the project root:

```bash
# 1. Base Schema
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < docs/database/schema_v2.sql

# 2. Seed Categories (3-level hierarchy)
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < docs/database/01_category_seed.sql

# 3. Seed Products (catalog data)
mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < docs/database/02_product_seed.sql
```

## 4. Start the Backend (Spring Boot)

```bash
cd raw-ego
./mvnw spring-boot:run
```

- API Base URL: `http://localhost:8080`
- Swagger UI (API Docs): `http://localhost:8080/docs`

**Backend Check:**
- Ensure the logs show `Started RawEgoApplication` without terminating.
- If it fails connecting to `localhost:3307`, ensure the MySQL Docker container is running.

## 5. Sync Elasticsearch Index

Because the MySQL database was populated directly via SQL dumps, the Elasticsearch outbox was bypassed. You must run a manual reindex to make products searchable in the frontend.

1. Open `http://localhost:8080/docs` (Swagger UI)
2. Call `POST /api/v1/auth/login` with:
   ```json
   {
     "email": "admin@ego.com",
     "password": "Admin@123"
   }
   ```
3. Copy the `accessToken`.
4. Click **Authorize** at the top of Swagger UI and paste the token.
5. Call `POST /api/v1/admin/search/reindex`.
   Expected response: `"Reindex complete. N products indexed."`

## 6. Start the Frontend (React / Vite)

In a new terminal window:

```bash
cd raw-ego-frontend
npm install
npm run dev
```

- Storefront UI: `http://localhost:5173`
- Admin Portal: `http://localhost:5173/admin`

**Frontend Check:**
- Open `http://localhost:5173`. You should see the homepage.
- Search for "Tee" — if results appear, Elasticsearch sync was successful.

## Default Credentials

| Role | Email | Password |
|---|---|---|
| Admin | `admin@ego.com` | `Admin@123` |
| Customer | `rithik@ego.com` | `Secure@123` |
