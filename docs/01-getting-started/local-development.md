# Local Development Guide

This guide walks any developer — regardless of prior experience with this project — through
running the full EGO stack locally. Follow every step in order.

**Time estimate:** ~20–30 minutes on a fresh machine.

---

## Prerequisites

Before you begin, make sure the following four tools are installed on your machine.
Each section below explains how to install it, why it is needed, and how to confirm the install worked.

---

### 1. Java 21 JDK

**Why:** The EGO backend (`raw-ego`) is a Spring Boot application written in Java 21.
Java must be installed on your machine to compile and run the backend.

**Download:** [https://adoptium.net/temurin/releases/?version=21](https://adoptium.net/temurin/releases/?version=21)

> We recommend **Eclipse Temurin 21** (by Adoptium) — it is free, open-source, and works on Windows, macOS, and Linux.

**Install steps:**

- **Windows:** Download the `.msi` installer. Run it and follow the wizard. Make sure to check ✅ "Set JAVA_HOME variable" and ✅ "Add to PATH" during install.
- **macOS:** Download the `.pkg` installer and run it. Or use Homebrew: `brew install --cask temurin@21`
- **Linux (Ubuntu/Debian):** `sudo apt-get install temurin-21-jdk` (after adding the Adoptium repo)

**Verify the install:**
```bash
java -version
```
Expected output (version number should start with `21`):
```
openjdk version "21.0.x" ...
```

---

### 2. Node.js 20 (LTS)

**Why:** The EGO frontend (`raw-ego-frontend`) is a React/Vite application. Node.js is required
to install dependencies (`npm install`) and run the development server (`npm run dev`).

**Download:** [https://nodejs.org/en/download](https://nodejs.org/en/download)

> Download the **LTS (Long-Term Support)** version. Select **v20** or higher.

**Install steps:**

- **Windows / macOS:** Download the installer (`.msi` for Windows, `.pkg` for macOS) and run it.
- **Linux:** `sudo apt-get install nodejs npm` or use [nvm](https://github.com/nvm-sh/nvm) for version management.

**Verify the install:**
```bash
node -v
npm -v
```
Expected output:
```
v20.x.x
10.x.x
```

---

### 3. Docker Desktop

**Why:** The EGO platform depends on three infrastructure services — MySQL, Redis, and Elasticsearch.
Docker Desktop lets you run these as isolated containers without installing them directly on your machine.

**Download:** [https://www.docker.com/products/docker-desktop/](https://www.docker.com/products/docker-desktop/)

> After installing, open Docker Desktop and wait for it to show the green "Engine running" status before proceeding.

**Verify:**
```bash
docker -v
```
Expected output: `Docker version 24.x.x` or higher.

---

### 4. Git

**Why:** To clone this repository to your machine.

**Download:** [https://git-scm.com/downloads](https://git-scm.com/downloads)

**Verify:**
```bash
git --version
```

---

## Step 1 — Clone the Repository

```bash
git clone https://github.com/your-username/EGO_E-commerce.git
cd EGO_E-commerce
```

> Replace `your-username` with the actual GitHub username or organisation where the repo is hosted.

---

## Step 2 — Start Infrastructure Services (Docker)

The EGO platform needs MySQL, Redis, and Elasticsearch running before the backend can start.
All three run as Docker containers.

**Run these three commands from any terminal (order does not matter):**

```bash
# MySQL 8 — runs on port 3307 (intentionally not 3306, to avoid conflicts with any local MySQL install)
docker run -d --name rawego \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=rawego \
  -p 3307:3306 \
  mysql:8.0

# Redis 7 — in-memory store for cart and checkout locks
docker run -d --name ego-redis \
  -p 6379:6379 \
  redis:7-alpine

# Elasticsearch 9.0.1 — powers product search
docker run -d --name ego-elasticsearch \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -p 9200:9200 \
  docker.elastic.co/elasticsearch/elasticsearch:9.0.1
```

> **Windows PowerShell note:** If the multi-line `\` syntax does not work, run each command as a single line.

> **Elasticsearch memory note:** Elasticsearch 9.x requires at least **2 GB of RAM** allocated to Docker.
> Open Docker Desktop → Settings → Resources → Memory → set to at least 2048 MB, then restart Docker.

**✅ Verify all three containers started correctly:**

```bash
docker ps
```

You should see output with **three running containers:**

```
CONTAINER ID   IMAGE                                     ...   NAMES
xxxxxxxxxxxx   mysql:8.0                                 ...   rawego
xxxxxxxxxxxx   redis:7-alpine                            ...   ego-redis
xxxxxxxxxxxx   elasticsearch/elasticsearch:9.0.1        ...   ego-elasticsearch
```

> If a container is missing, re-run its `docker run` command above.
> If `ego-elasticsearch` is listed but the backend still cannot connect, wait 30–60 seconds and try again — Elasticsearch takes longer to initialize than MySQL or Redis.

---

## Step 3 — Configure Environment Variables

The backend and frontend each need an environment file with API keys and secrets.

### 3a. Backend environment file

From the **project root**, run:

```bash
# macOS / Linux / Git Bash:
cp raw-ego/.env.example raw-ego/.env

# Windows PowerShell:
Copy-Item raw-ego\.env.example raw-ego\.env
```

Open `raw-ego/.env` in any text editor and fill in the following values:

#### Generate a JWT Secret (Required — app will not start without this)

`JWT_SECRET` must be a 64-character (256-bit) random hex string. Choose one method:

**Option A — Online (easiest):**
Visit [https://jwtsecrets.com/#generator](https://jwtsecrets.com/#generator), select **256-bit**, and copy the result.

**Option B — Windows PowerShell:**
```powershell
[System.BitConverter]::ToString((1..32 | ForEach-Object { [byte](Get-Random -Max 256) })).Replace("-","").ToLower()
```

**Option C — macOS / Linux / Git Bash:**
```bash
openssl rand -hex 32
```

Paste the result into your `.env`:
```env
JWT_SECRET=paste_your_64_character_hex_string_here
```

#### Enable Swagger UI (Required for Step 6 — Elasticsearch reindex)

Add this line to your `raw-ego/.env`:
```env
SWAGGER_ENABLED=true
```

> Swagger UI is disabled by default in production. Setting this in your local `.env` enables the interactive
> API explorer at `http://localhost:8080/docs`, which you will need in Step 6.

#### Other service keys

For full local functionality, also set these in `raw-ego/.env`:
```env
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

RAZORPAY_KEY_ID=rzp_test_yourkey
RAZORPAY_KEY_SECRET=your_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

SENDGRID_API_KEY=SG.your_sendgrid_key
SENDGRID_FROM_EMAIL=your_verified_sender@domain.com
```

> The app will start without Cloudinary, Razorpay, and SendGrid keys, but image uploads, payments, and emails will not work.

---

### 3b. Frontend environment file

From the **project root**, run:

```bash
# macOS / Linux / Git Bash:
cp raw-ego-frontend/.env.example raw-ego-frontend/.env.local

# Windows PowerShell:
Copy-Item raw-ego-frontend\.env.example raw-ego-frontend\.env.local
```

The default values in `.env.example` are already correct for local development.
Your `raw-ego-frontend/.env.local` should contain:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_CLOUDINARY_CLOUD_NAME=your-cloudinary-cloud-name
VITE_RAZORPAY_KEY_ID=rzp_test_your_key_id
```

> `VITE_API_BASE_URL` points to the backend running on port 8080. Do not change this for local development.
> Fill in your Cloudinary cloud name and Razorpay test key if you want those features to work in the UI.

---

## Step 4 — Seed the Database

### Why this step is needed

The `rawego` MySQL container starts with an **empty database** — no tables, no data.
This step imports two files into the running container:

| File | What it contains |
|---|---|
| `schema_v2.sql` | All table definitions (schema only, no data) |
| `seed_data.sql` | Full dataset — products, categories, users, inventory, orders, and more |

Run these **in order** (schema must exist before data can be inserted).

### What is the MySQL Client (`mysql`) and why do you need it?

The `mysql` command is a command-line tool used to execute SQL files against a MySQL server.
It is **separate** from Docker and MySQL Server.

If you followed this guide and only installed Docker (not MySQL locally), you likely do **not** have the
`mysql` command available. Trying to run the seed command will produce this error:

```
'mysql' is not recognized as an internal or external command, operable program or batch file.
```

**You have two options. Pick the one that suits you:**

---

#### Option A — Use Docker (No additional install required) ✅ Recommended

Docker lets you run the `mysql` client *inside* the already-running container, without installing
anything on your machine.

Run these **two commands** from the **project root** (`EGO_E-commerce/`):

```bash
# 1. Create all tables (schema)
docker exec -i rawego mysql -u root -proot rawego < docs/06-database/schema_v2.sql

# 2. Load the full dataset (products, categories, users, inventory, orders…)
docker exec -i rawego mysql -u root -proot rawego < docs/06-database/seed_data.sql
```

> **Windows PowerShell note:** The `<` redirect operator may not work in PowerShell for piping files to Docker.
> Use **Git Bash** (installed with Git for Windows) to run these commands, or switch to Option B below.

---

#### Option B — Install the MySQL Client Separately

If you prefer having the `mysql` command available globally on your machine:

1. Go to [https://dev.mysql.com/downloads/mysql/](https://dev.mysql.com/downloads/mysql/)
2. Download **MySQL Community Server 8.0** for your OS.
   - On the download page, click **"No thanks, just start my download"** (no account required).
3. During installation, when asked for a setup type, choose **"Custom"** and install only:
   - ✅ MySQL Server (needed for the `mysql` CLI client)
   - ❌ MySQL Workbench (optional GUI — not needed)
4. Complete the installer. **Important:** Do NOT start MySQL Server as a Windows service — EGO uses its own Docker container on a different port (3307). You only need the client CLI tool.
5. After install, open a new terminal and verify:
   ```bash
   mysql --version
   ```
   Expected: `mysql  Ver 8.0.x ...`

6. Now run the two seed commands from the **project root** (`EGO_E-commerce/`):
   ```bash
   # 1. Create all tables (schema)
   mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < docs/06-database/schema_v2.sql

   # 2. Load the full dataset
   mysql -u root -proot -h 127.0.0.1 -P 3307 rawego < docs/06-database/seed_data.sql
   ```

---

**✅ Verify seeding worked:**

```bash
docker exec -it rawego mysql -u root -proot rawego -e "SHOW TABLES;"
```

You should see **23 tables** listed (e.g., `users`, `products`, `categories`, `orders`...).
If you see an empty list or an error, re-run the seed commands above.

---

## Step 5 — Start the Backend (Spring Boot)

### Option A — Using the Terminal (Command Line)

Open a terminal, go to the backend folder, and start the Spring Boot application:

**macOS / Linux / Git Bash (Windows):**
```bash
cd raw-ego
./mvnw spring-boot:run
```

**Windows — PowerShell or Command Prompt:**
```powershell
cd raw-ego
.\mvnw.cmd spring-boot:run
```

> `mvnw` / `mvnw.cmd` is the Maven Wrapper — a script bundled with the project that downloads
> the correct Maven version automatically. You do **not** need to install Maven separately.

---

### Option B — Using an IDE (Recommended for Development) 🎯

Using an IDE is easier than the command line — it shows logs clearly, lets you restart with one click,
and gives you full debugging.

**Recommended IDE: IntelliJ IDEA Community Edition** (free)
- Download: [https://www.jetbrains.com/idea/download/](https://www.jetbrains.com/idea/download/)
- Choose **Community Edition** — it's free and fully supports Spring Boot.

**Steps to run from IntelliJ:**
1. Open IntelliJ IDEA → Click **"Open"** → Select the `raw-ego/` folder.
2. IntelliJ will auto-detect the Maven project and download dependencies. Wait for indexing to finish.
3. Find the file `RawEgoApplication.java` (in `src/main/java/com/ego/raw_ego/`).
4. Click the **green Run ▶ button** next to the class name, or right-click → **Run 'RawEgoApplication'**.
5. The **Run panel** at the bottom will show the application logs.

> **Adding the .env file in IntelliJ:**
> IntelliJ does not automatically read `.env` files. Install the **"EnvFile"** plugin:
> Go to **File → Settings → Plugins** → search for **"EnvFile"** → Install.
> Then in the Run Configuration (Edit Configurations), enable **EnvFile** and point it to `raw-ego/.env`.

---

**✅ Backend started successfully when you see this in the logs:**
```
Started RawEgoApplication in X.XXX seconds
```

- API Base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/docs` (requires `SWAGGER_ENABLED=true` in `.env`)

---

## Step 6 — Sync Elasticsearch (Enable Search)

Because the database was seeded directly via SQL files, the Elasticsearch search index is empty.
You must trigger a manual reindex to make products searchable.

1. Open Swagger UI: **[http://localhost:8080/docs](http://localhost:8080/docs)**
2. Find the `POST /api/v1/auth/login` endpoint and click **"Try it out"**.
3. Use the admin credentials:
   ```json
   {
     "email": "admin@ego.com",
     "password": "Admin@123"
   }
   ```
4. Execute the request. Copy the `accessToken` from the response.
5. Click the **🔒 Authorize** button at the top of the Swagger page.
6. Paste the token in the **"Bearer"** field and click **Authorize**.
7. Find `POST /api/v1/admin/search/reindex` and click **"Try it out"** → **"Execute"**.
8. Expected response: `"Reindex complete. N products indexed."`

---

## Step 7 — Start the Frontend (React / Vite)

Open a **new terminal window** (keep the backend running in the previous one):

```bash
cd raw-ego-frontend
npm install
npm run dev
```

- **Storefront:** [http://localhost:5173](http://localhost:5173)
- **Admin Portal:** [http://localhost:5173/admin](http://localhost:5173/admin)

**✅ Frontend started successfully when:**
- `http://localhost:5173` shows the EGO homepage.
- Searching for "Tee" returns products (confirms Elasticsearch sync worked).

---

## Default Credentials

| Role | Email | Password |
|---|---|---|
| Admin | `admin@ego.com` | `Admin@123` |
| Customer | `rithik@ego.com` | `Secure@123` |

---

## Troubleshooting

If something goes wrong, see the full troubleshooting guide:
**[docs/10-troubleshooting/common-issues.md](../10-troubleshooting/common-issues.md)**

**Quick reference for the most common issues:**

| Error | Likely Cause | Fix |
|---|---|---|
| `Connection refused: localhost:3306` | MySQL container not running | Run `docker ps`, restart `rawego` container |
| `RedisConnectionFailureException` | Redis container not running | Run `docker ps`, restart `ego-redis` |
| Elasticsearch connection refused | ES container down or not ready yet | Wait 60 seconds, then restart backend |
| `'mysql' is not recognized` | MySQL CLI not installed | Use Option A (docker exec) in Step 4 |
| `./mvnw: Permission denied` | Script not executable | Run `chmod +x ./mvnw` then retry |
| `.\mvnw.cmd` fails on PowerShell | Execution policy | Run `Set-ExecutionPolicy RemoteSigned` |
| Swagger UI shows 404 | `SWAGGER_ENABLED` not set | Add `SWAGGER_ENABLED=true` to `raw-ego/.env` |
| Frontend shows CORS error | Backend not running | Start the backend first (Step 5) |
