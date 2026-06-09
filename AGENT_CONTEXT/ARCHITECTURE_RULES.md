# Architecture & Engineering Rules — EGO Platform

This document outlines the strict architectural and engineering boundaries of the EGO E-commerce platform. All code modifications and future additions must comply with these guidelines.

---

## 1. Core Architectural Strategy

### Decoupled Monolithic Design
The backend is a Modular Monolith. Code must be grouped strictly inside package structures representing functional areas (e.g., `auth`, `catalog`, `inventory`).
* **Directional Dependencies Only:** Feature packages must minimize cross-package dependencies. Communication between modules should ideally occur via public interfaces or decoupled application events.
* **Database Isolation:** Entities are bound to their respective modules. Avoid multi-package tables or unconstrained joins across modules.

---

## 2. Backend Architecture Rules

### Package Structure Standards
Every module must conform to the following directory layout:
```
com.ego.raw_ego.{module_name}/
├── entity/       ← JPA entity models
├── repository/   ← Spring Data JPA interfaces
├── service/      ← Business logic interfaces/implementations
├── controller/   ← REST controllers (exposing DTOs only)
├── dto/
│   ├── request/  ← Input requests (validated using jakarta.validation)
│   └── response/ ← Standardized output envelopes
└── enums/        ← Module-specific enums
```

### Transaction Management
* **Mutations:** Any service method modifying the database must be annotated with `@Transactional`.
* **Read-Only Operations:** Use `@Transactional(readOnly = true)` on GET operations to improve Hibernate performance and database read performance.
* **Granularity:** Keep transactions tight. Do not wrap slow external network calls (e.g., Cloudinary upload, Razorpay payment verification) inside SQL transactional contexts to prevent connection pool starvation.

### DTO Isolation & Boundaries
* **Rule:** Entities (JPA mapping classes) must **never** escape the Service layer. Controllers must strictly consume Request DTOs and return Response DTOs.
* **Rationale:** Direct entity exposure leaks database structures, risks recursive JSON serialization loops, and exposes private data (e.g., variant `costPrice` or user passwords).

### Input Validation
* **Enforcement:** DTO properties must have explicit constraints (`@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@DecimalMin`, `@Pattern`).
* **Execution:** Controllers must annotate request payloads with `@Valid` to trigger standard validation filters. Invalid inputs must be caught by `GlobalExceptionHandler` returning a `400 Bad Request` with structured error messages.

### Exception Handling
* **Standard Pattern:** Do not throw raw RuntimeExceptions or write custom try-catch blocks in controllers.
* **Strategy:** Business validation failures must throw a subclasses of `EgoException` (e.g., `ConflictException`, `ResourceNotFoundException`, `AuthException`). These map to appropriate HTTP Statuses.
* **Global Handler:** All exceptions are centralized in `GlobalExceptionHandler.java`. Standard, clean JSON response structures must always be returned. Stack traces are masked for security on 500 errors.

### Logging Conventions
* **Lombok Logging:** Annotate classes with `@Slf4j`.
* **Prohibited:** Never use `System.out.println()` or `ex.printStackTrace()`.
* **Syntax:** Use parameterized logging: `log.info("Message here: {}", parameter)`. Avoid string concatenation within log statements to keep memory footprint minimal.
* **Levels:** Use `INFO` for critical business markers (e.g. order confirmation), `WARN` for validation issues/non-critical failures, and `ERROR` only for system failures requiring developer attention.

---

## 3. Frontend Architecture Rules

### Package Directory Structure
Frontend directories are structured by technical responsibility under `src/`:
```
src/
├── api/        ← Axios apiClient and endpoint modules
├── components/ ← Reusable atomic UI components
├── features/   ← Feature-specific code blocks (e.g., auth, catalog)
├── hooks/      ← Custom shared hooks
├── pages/      ← Page-level components
├── store/      ← Zustand state stores
├── theme/      ← MUI CSS overrides and styles
├── types/      ← TypeScript interfaces/types
└── utils/      ← Helper functions and transformers
```

### State Division of Labor
* **Client-Side State:** Stored inside lightweight Zustand stores (e.g., `authStore` for auth states, `uiStore` for toast/navigation states).
* **Server-Side Cache:** Stored inside TanStack Query (React Query). Never fetch remote backend APIs directly inside `useEffect` blocks. Leverage hooks (`useQuery`, `useMutation`) to handle state synchronization, caching, and retries.

### Axios Pipeline Config
* **API base client:** Defined inside `src/api/client.ts`.
* **Tokens handling:** Request interceptor attaches the JWT token dynamically.
* **Response 401 interceptor:** Monitors request failures. Uses a queue pattern to block subsequent calls while performing a silent refresh. Ensures only ONE refresh call is dispatched to prevent token rotation family revocations.
* **Errors integration:** Maps error payloads into standardized notifications using Zustand toast interfaces.

### Styling System
* Use Vanilla CSS and CSS modules.
* Styling tokens are defined globally within the MUI theme system (`src/theme`). Standard color schemes (Harmonious HSL values), rounded surfaces (curved widgets), and high-quality typefaces are enforced. Avoid hardcoding random styles inside components.
