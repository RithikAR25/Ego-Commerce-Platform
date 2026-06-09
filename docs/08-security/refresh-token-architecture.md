# Refresh Token Architecture — EGO Platform

This document details the mechanics, rationale, and security benefits of the Refresh Token implementation in the EGO E-commerce platform.

---

## 1. The Core Problem: Security vs. UX

In a stateless authentication system, the backend does not look up the database on every request to verify if a user is still logged in. Instead, it relies on a cryptographic signature on a **JSON Web Token (JWT)**.

If we only used a single token, we would face an impossible tradeoff:
* **Long-lived Token (e.g., 30 days):** Excellent UX (user stays logged in), but terrible security. If stolen, a hacker has unrestricted access for 30 days. The only way to revoke it is to check a database on every single API request, which destroys the performance benefits of stateless JWTs.
* **Short-lived Token (e.g., 15 minutes):** Excellent security (the token becomes useless quickly), but terrible UX. The user has to log in again every 15 minutes.

## 2. The Solution: The Token Pair

The EGO platform solves this using a **Token Pair** architecture:

1. **Access Token (AT):** 
   * **Lifespan:** Short (15 minutes).
   * **Usage:** Sent by the frontend in the `Authorization: Bearer <token>` header on every API request. 
   * **Validation:** Validated entirely in-memory using math (cryptographic signature) in microseconds. No database lookup required.

2. **Refresh Token (RT):** 
   * **Lifespan:** Long (30 days).
   * **Usage:** Stored securely by the frontend. Used *only* to request a new Access Token when the old one expires.
   * **Validation:** Validated against the database to ensure it hasn't been revoked.

## 3. The Refresh Flow (Silent Refresh)

From the user's perspective, they log in once and stay logged in. Here is what happens under the hood:

1. **Access Token Expires:** The frontend makes an API request (e.g., fetching a product), but the 15-minute AT has expired. The backend returns a `401 Unauthorized`.
2. **Silent Post Request:** The frontend intercepts this `401` error. Behind the scenes, it immediately makes a `POST` request to `/api/v1/auth/refresh`, sending the long-lived Refresh Token in the request body.
3. **Backend Validation & Rotation:**
   * The backend hashes the received RT and looks it up in the `refresh_tokens` database table.
   * If valid, it **revokes** that specific RT and generates a brand new RT (Token Rotation).
   * It generates a brand new AT.
4. **Resumption:** The backend returns the new AT and new RT to the frontend. The frontend updates its local storage and automatically retries the original failed API request with the new AT.

**Result:** The user experiences a seamless session, while the system maintains high security.

---

## 4. Advanced Security Features (EGO Implementation)

The EGO platform implements an enterprise-grade refresh token system with the following hardened security features:

### Token Rotation
Every time a refresh token is used to get a new access token, it is immediately discarded and replaced with a new one. A specific refresh token string can only be used *once*. This drastically limits the useful lifespan of a stolen refresh token.

### Theft Detection (Token Family Revocation)
When a user logs in, they start a "token family" (tracked via a `family_id` UUID in the database). When a token is rotated, the new token stays in the same family.

If a hacker steals a refresh token and uses it, they get a new token pair. However, the original token is now marked as revoked in the database. 
When the *legitimate* user's app eventually wakes up and tries to use their (now spent) original refresh token, the backend detects the anomaly (attempting to use a revoked token).
**Response:** The backend instantly revokes the *entire token family*. Both the hacker and the legitimate user are immediately logged out, neutralizing the threat.

### Cryptographic Storage (SHA-256)
The raw Refresh Token string (a UUID) is returned to the client, but the backend **never stores the raw string in the database**. It hashes the token using SHA-256 and only stores the hash. 
If the EGO database is ever compromised, the attackers only get hashes, which cannot be used to impersonate users on the live system.

### Targeted Logout
Because refresh tokens are tracked individually in the database, when a user clicks "Logout" on their phone, the frontend calls `POST /api/v1/auth/logout`. The backend deletes *only* the refresh token associated with that specific device. The user remains logged in on their desktop browser.
