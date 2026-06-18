# AI Agent Workspace Instructions — EGO Platform

General coding standards: https://github.com/RithikAR25/dev-standards

Welcome, AI Agent! This document outlines the mandatory operational standards, constraints, and instructions you must follow when working on the EGO E-commerce codebase.

---

## 1. Onboarding & Context Loading Protocol

Before generating code or suggesting modifications, you must read the persistent context files located in this directory:

1. **`PROJECT_OVERVIEW.md`:** Understand the business domain, locked decisions, and architecture boundaries.
2. **`CURRENT_STATUS.md`:** Read about implemented modules, fixed bugs, and system parameters.
3. **`TECH_STACK.md`:** Know the exact framework versions and libraries available.
4. **`ARCHITECTURE_RULES.md` & `IMPLEMENTATION_RULES.md`:** Adhere to the code quality rules.
5. **`TODO_NEXT_PHASE.md`:** Read the implementation steps for the current target phase.
6. **`PATTERNS_LIBRARY.md`:** *(Optional but recommended)* Maps every reusable pattern in EGO to its source file and the corresponding general standard in `dev-standards`. Read this when you need to understand *why* a pattern exists or find a specific implementation.

---

## 2. Mandatory Coding Constraints

### Backend Coding Mandates (Java / Spring Boot)

- **Constructor Injection Only:** Use Lombok's `@RequiredArgsConstructor` on `final` fields. Do not use field-level `@Autowired`.
- **Standard Response Envelopes:** Every controller endpoint must return `ResponseEntity<ApiResponse<T>>`. Never return raw entities or domain records directly.
- **Granular Transactions:** Do not perform external HTTP calls (such as uploads, notifications, or payment gateway queries) inside a database transactional context (`@Transactional`).
- **Clean Hibernate Bidirectional Saves:** When persisting parent-child entities with cascade rules (e.g., Variant and Inventory), you **must** wire both sides of the relationship in memory **before** executing the save.
- **No Raw SQL:** All database queries must go through JPA repository methods or parameterized `@Query` annotations.

### Frontend Coding Mandates (TypeScript / React)

- **Strict Typing:** Never use the `any` type. Define explicit types or interfaces for all props, states, and API responses.
- **TanStack Query hooks:** Wrap all backend REST requests in custom hooks. Component layers must never perform raw Axios queries inside `useEffect` blocks.
- **Axios Interceptor client:** Utilize `apiClient` (`src/api/client.ts`) for all endpoints. Respect the queued silent-refresh loops and token expiration configurations.
- **Theme-Based Styling:** All visual surfaces must consume HSL parameters and styling properties directly from the theme definitions. Never hardcode random hex values, margins, or paddings within component files.

---

## 3. Strictly Forbidden Actions

- ❌ **Do NOT modify Locked Architecture Decisions** (e.g., flat 2-level category structure, SKU layout, 3-tier decimals pricing) without explicit instructions.
- ❌ **Do NOT expose `cost_price`** in any storefront/public REST responses. This data must remain strictly hidden behind administrative authentication guards.
- ❌ **Do NOT write global `/**`wildcards** in`SecurityConfig.PUBLIC_MATCHERS` over authentication or administrative controllers. List specific unauthenticated paths explicitly.
- ❌ **Do NOT clear authentication states on generic server failures.** Only invoke session cleanup operations (`clearAuth()`) if the refresh token itself is invalid or has expired.
- ❌ **Do NOT bypass the Axios interceptor.** Never write direct fetch operations that bypass the silent-refresh pipeline.
- ❌ **Do NOT add random third-party CSS or UI libraries** (e.g., TailwindCSS) unless explicitly asked by the user. Rely entirely on the pre-configured MUI and custom HSL CSS overrides.

---

## 4. Documentation Strategy

If you modify endpoints, database schemas, or visual flows, you **must** update the corresponding documentation files:

- If you modify API paths or response payloads, update **`API_CONTRACTS.md`**.
- If you resolve critical errors or implement new module verification routines, update **`docs/backend/catalog-api-swagger-testing-guide.md`**.
- If you complete a development phase, update **`CURRENT_STATUS.md`** and **`TODO_NEXT_PHASE.md`** to maintain an accurate codebase roadmap.
