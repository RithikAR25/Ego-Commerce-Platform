# EGO API Reference

The EGO platform uses an OpenAPI 3.0 (Swagger) driven documentation strategy. The backend automatically generates an interactive REST API reference based on the actual Java source code and Jakarta validation annotations.

Because the source code is the only truth, we do not maintain static markdown API endpoint tables that could drift from reality.

---

## 🧭 How to Access the Live API Docs

To view the complete, interactive API documentation:

1. **Start the backend server:**
   ```bash
   cd raw-ego
   ./mvnw spring-boot:run
   ```
2. **Open the Swagger UI in your browser:**
   [http://localhost:8080/docs](http://localhost:8080/docs)

---

## 🔒 Authentication in Swagger UI

Most endpoints require a JWT Access Token. To test these endpoints directly within the Swagger UI:

1. Scroll to the **Auth** tag.
2. Expand `POST /api/v1/auth/login`.
3. Click **Try it out** and use the default admin credentials:
   ```json
   {
     "email": "admin@ego.com",
     "password": "Admin@123"
   }
   ```
4. Click **Execute** and copy the `accessToken` from the response body.
5. Scroll to the top of the page and click the green **Authorize** button.
6. Paste the token into the `bearerAuth` field and click **Authorize**.

You can now execute requests against any protected `/api/v1/admin/**` or `/api/v1/users/me/**` endpoints.

---

## 📦 Global Response Envelope

Every endpoint (including error responses) returns a standardized JSON envelope to simplify frontend parsing:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "errors": null,
  "timestamp": "2026-06-08T12:00:00Z"
}
```

### Field Definitions:
* `success`: Boolean indicating if the business operation succeeded.
* `message`: Human-readable summary (e.g., "Product added to cart").
* `data`: The actual payload object or array. Null on errors.
* `errors`: Only present on 400 Bad Request validation failures. An array of objects detailing which fields failed validation.
* `timestamp`: ISO-8601 timestamp of the response.

---

## 🚦 Common Status Codes

| Code | Meaning | Context |
| :--- | :--- | :--- |
| **200** | OK | Standard success for GET, PUT, PATCH, and DELETE. |
| **201** | Created | Standard success for POST (resource creation). |
| **400** | Bad Request | Validation failure (check the `errors` array) or malformed payload. |
| **401** | Unauthorized | Missing, expired, or invalid JWT token. The frontend intercepts this to trigger a silent refresh. |
| **403** | Forbidden | Valid token provided, but the user lacks the required `ROLE_ADMIN`. |
| **404** | Not Found | Requested resource ID or URL slug does not exist. |
| **409** | Conflict | Business logic violation (e.g., trying to capture payment on a cancelled order). |
| **500** | Server Error | Unhandled exception. |

---

## 📑 Domain Modules Overview

The API is segmented under `/api/v1/` into the following domains:

* `/auth`: Login, registration, token refresh, and logout.
* `/categories`: Public category tree traversal.
* `/products`: Public product catalog and faceted search.
* `/cart`: Redis-backed shopping cart management.
* `/orders`: Customer order placement and historical retrieval.
* `/payments`: Razorpay integration and webhook callbacks.
* `/admin/*`: Secure area for inventory, order fulfillment, and system administration.
