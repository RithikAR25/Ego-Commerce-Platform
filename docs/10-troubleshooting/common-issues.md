# Troubleshooting & Common Issues

This guide details common issues encountered during local development and testing, along with their resolutions.

---

## 🛑 Backend Initialization Errors

### 1. `Connection refused: localhost:3306`
* **Symptoms:** Spring Boot crashes immediately on startup with Hikari pool connection exceptions.
* **Cause:** The MySQL Docker container is not running, or is mapped to the wrong port.
* **Resolution:** 
  1. Check `docker ps`.
  2. Ensure the container was started with `-p 3307:3306` (EGO backend connects to `3307` locally to avoid conflicts with local MySQL installs).

### 2. `RedisConnectionFailureException`
* **Symptoms:** Stack trace citing unable to connect to `localhost:6379`.
* **Cause:** Redis container is not running.
* **Resolution:** Run `docker run -d --name ego-redis -p 6379:6379 redis:7-alpine`.

### 3. `ElasticsearchStatusException: Connection refused`
* **Symptoms:** Backend boots, but any search query or catalog save operation throws an ES exception.
* **Cause:** Elasticsearch container is down.
* **Resolution:** Ensure the ES container is running. Note that Elasticsearch 9.x requires at least 2GB of RAM allocated to Docker.

---

## 🔐 Authentication & JWT Issues

### 1. Infinite Login Loop / Immediate 401s
* **Symptoms:** Upon successful login in the frontend, the page immediately redirects back to the login screen.
* **Cause:** The `refreshToken` cookie is not being saved or sent. This happens if the frontend and backend are not running on `localhost` (e.g., testing via IP address without HTTPS). Secure cookies are rejected by browsers over non-HTTPS connections unless the domain is exactly `localhost`.
* **Resolution:** Ensure both your Vite server and Spring Boot server are accessed strictly via `http://localhost`.

### 2. Family Token Revocation Triggered
* **Symptoms:** You were logged in, but suddenly get logged out. The backend logs show `[AUTH] Refresh Token Reuse Detected! Family revoked for user X`.
* **Cause:** A race condition occurred where the frontend attempted to refresh the token twice concurrently, or an old token was intercepted and used.
* **Resolution:** The frontend Axios queue interceptor usually prevents this. Clear your browser cookies and log in again.

---

## 🛒 E-Commerce Flows

### 1. Payment Webhook Signature Exception
* **Symptoms:** Razorpay dashboard shows webhooks failing with `400 Bad Request`. Backend logs cite `WebhookSignatureException`.
* **Cause:** The `ego_razorpay_webhook_secret` in your `.env` does not match the secret configured in the Razorpay Webhook dashboard.
* **Resolution:** Update your `.env` file with the correct secret and restart the backend.

### 2. Images Not Loading (Cloudinary)
* **Symptoms:** Product detail pages show broken image links.
* **Cause:** The `CLOUDINARY_CLOUD_NAME` is missing or incorrect, resulting in malformed image URLs being saved to the database.
* **Resolution:** Ensure your `.env` contains your exact Cloudinary cloud name. Re-upload the product image through the Admin Portal.

### 3. "Insufficient Inventory" During Checkout
* **Symptoms:** Cart shows items, but checking out throws a 409 Conflict for inventory.
* **Cause:** Another process holds the Redis `SETNX` lock for that variant, or the database `stock` column is literally 0.
* **Resolution:** If testing concurrency, wait 5 minutes for the Redis lock to expire natively, or clear Redis (`docker exec -it ego-redis redis-cli FLUSHALL`).

---

## 🔍 Frontend Development

### 1. Vite HMR (Hot Module Replacement) Not Working
* **Symptoms:** Saving a React component does not update the browser.
* **Cause:** Casing mismatch in import statements (Windows is case-insensitive, Vite/Linux is not).
* **Resolution:** Check terminal logs for `Does not match the corresponding name on disk`. Correct the import path casing.

### 2. CORS Errors in Browser Console
* **Symptoms:** `Access to XMLHttpRequest at 'http://localhost:8080/...' from origin 'http://localhost:5173' has been blocked by CORS policy.`
* **Cause:** The backend CORS configuration is not allowing the frontend origin.
* **Resolution:** Verify `CorsConfig.java` in the backend explicitly allows `http://localhost:5173`.
