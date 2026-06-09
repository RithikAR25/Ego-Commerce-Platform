# EGO — Premium D2C Streetwear Platform

![EGO Platform Overview](docs/diagrams/placeholder-hero-image.png)
> *An end-to-end, high-performance e-commerce platform built for scale, speed, and visual excellence.*

**EGO** is a full-stack, direct-to-consumer (D2C) streetwear retail platform. It features a modern, stateless architecture designed to handle high-concurrency inventory drops, complex product variants, and instant faceted search.

---

## 🌟 Key Features

* **Instant Faceted Search:** High-speed product filtering and search powered by **Elasticsearch**, separated from the primary transactional database.
* **Strict Inventory Control:** Prevents double-sells during high-traffic drops using **Redis distributed locks** and MySQL optimistic locking.
* **Stateless Security:** Complete JWT-based authentication with automated silent refresh and family token revocation (no sticky sessions).
* **Dynamic Media Delivery:** Automated image transformations, cropping, and WebP transcoding via **Cloudinary CDN**.
* **Seamless Checkout:** Integrated with **Razorpay** (UPI, Cards) with secure, idempotent webhook processing.
* **Event-Driven Notifications:** Asynchronous, non-blocking transactional emails (SendGrid) fired via Spring Application Events.

---

## 🏗️ Architecture Stack

### Backend (`raw-ego`)
* **Core:** Java 21 / Spring Boot 4.x
* **Data:** MySQL 8 (Transactional), Redis 7 (Cart/Locks), Elasticsearch 9.x (Search)
* **Security:** Spring Security 7 (Stateless JWT)
* **Integrations:** Razorpay (Payments), Cloudinary (CDN), SendGrid (Email)

### Frontend (`raw-ego-frontend`)
* **Core:** React 19 / TypeScript 5 / Vite 8
* **State Management:** Zustand (Local) + TanStack Query v5 (Server)
* **Styling:** Material-UI (MUI v9) + Framer Motion (Animations)
* **Routing & Validation:** React Router v7 + Zod

---

## 📸 Platform Gallery

### Storefront Experience
| Home Page | Product Detail Page (PDP) |
| :---: | :---: |
| ![Home](docs/diagrams/placeholder-home.png) | ![PDP](docs/diagrams/placeholder-pdp.png) |

| Cart & Checkout | Faceted Search |
| :---: | :---: |
| ![Checkout](docs/diagrams/placeholder-checkout.png) | ![Search](docs/diagrams/placeholder-search.png) |

### Admin Portal
| Inventory Management | Order Fulfillment |
| :---: | :---: |
| ![Admin Inventory](docs/diagrams/placeholder-admin-inventory.png) | ![Admin Orders](docs/diagrams/placeholder-admin-orders.png) |

---

## 🚀 Getting Started

Want to run EGO locally? It takes less than 5 minutes using Docker.

### Environment Configuration

Before starting the application, check whether the required environment file already exists:

**Backend:**
* If `.env` exists, use it.
* Otherwise create `.env` from `.env.example` and populate the required values.

**Frontend:**
* If `.env.local` exists, use it.
* Otherwise create `.env.local` from `.env.example` and populate the required values.

If environment files have already been provided, place them in the appropriate project directories and proceed to the next step. Otherwise, create them from the provided `.env.example` templates.

### Guides

1. **[Local Development Setup Guide](docs/01-getting-started/local-development.md)** — Step-by-step instructions for booting the MySQL/Redis/ES containers and starting the applications.
2. **[Backend README](raw-ego/README.md)** — Backend-specific build and test instructions.
3. **[Frontend README](raw-ego-frontend/README.md)** — Frontend-specific dev server and linting instructions.

---

## 📖 Comprehensive Documentation

This repository is extensively documented for developers and architects.

* **[System Architecture Overview](docs/02-architecture/system-overview.md)**
* **[Database Schema & Models](docs/06-database/schema-overview.md)**
* **[Authentication & JWT Flow](docs/08-security/jwt-flow.md)**
* **[Order Lifecycle State Machine](docs/04-flows/order-lifecycle.md)**
* **[Elasticsearch Synchronization](docs/07-search/elasticsearch.md)**
* **[API Reference Guide](docs/05-api/api-reference.md)**

> **Note for AI Agents / LLMs:**
> If you are an AI assistant analyzing this repository, start by reading the onboarding files in `docs/12-agents/` and the rules in `AGENT_CONTEXT/`.

---

## 🔒 Security Note

This is a portfolio project. All documentation and source code has been thoroughly audited for leaked credentials. 
* To run this project locally, you must provide your own Cloudinary, Razorpay, and SendGrid test API keys via a `.env` file (see `raw-ego/.env.example`).

---

*Designed and developed by Rithik. For inquiries, please review the contact information on my GitHub profile.*
