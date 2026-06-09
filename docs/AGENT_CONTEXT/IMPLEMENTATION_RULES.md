# Code Implementation Rules & Quality Standards — EGO Platform

This document outlines the coding standards, patterns, type systems, and quality expectations that must be enforced during the implementation of both the backend and frontend codebases.

---

## 1. Backend Implementation Standards (Java & Spring Boot)

### Java Coding Standards
* **Java Version:** Java 17 or 21 features (e.g. switch expressions, records, text blocks).
* **Code Formatting:** Follow standard Java naming conventions (PascalCase for classes, camelCase for variables/methods, UPPER_SNAKE_CASE for constants).
* **Lombok Usage:** Use `@Getter`, `@Setter`, `@Builder`, and `@Slf4j` to reduce boilerplate. Avoid using `@Data` on JPA entities to prevent recursion in `hashCode()` and `toString()`.

### Dependency Injection (DI)
* **Rule:** Always use **constructor injection**. Never use field injection (`@Autowired` on class fields).
* **Implementation:** Leverage Lombok's `@RequiredArgsConstructor` to generate constructors automatically for `final` fields.

### JPA & Database Conventions
* **Optimistic Locking:** Entity models representing transactional counters (like `InventoryRecord`) must carry a `@Version` field to prevent race conditions during concurrent modifications.
* **Cascades & Wiring:** Maintain clean bidirectional entity relationships. Relationships configured with `cascade = CascadeType.ALL` must be fully wired on both sides (parent and child references) **before** persisting to prevent transient property errors.
* **Raw SQL Prohibition:** Write queries using Spring Data JPA method signatures or parameterized `@Query` annotations. Never write raw SQL strings inside code to prevent SQL injection vulnerabilities.

---

## 2. Frontend Implementation Standards (TypeScript & React)

### TypeScript Conventions
* **Type Safety:** Maintain strict type definitions. The use of the `any` type is **strictly prohibited**.
* **Strict Null Checks:** Safely handle nullable variables. Leverage optional chaining (`?.`) and nullish coalescing (`??`) rather than assuming data is always present.
* **Component Declarations:** Define components as Functional Components with explicit React typing: `const MyComponent: React.FC<Props> = ({ props }) => { ... }`.

### State Store Integrations
* **Zustand Stores:** Keep state mutations predictable. Use the `set` function inside stores to update values. Avoid direct state mutations outside the store's action boundaries.
* **TanStack Query hooks:** Always wrap backend REST requests inside custom query hooks (e.g. `useProducts()`, `useProduct(slug)`). Never write raw fetch or axios calls directly inside React component `useEffect` blocks.

### Component Design Systems
* **Responsive Layouts:** Design using mobile-first strategies. Leverage MUI's responsive grid system and media query tools (`theme.breakpoints`).
* **Visual Standards:** Rounded components (`borderRadius: '12px'`) and curated colors must be imported directly from the theme system. Avoid hardcoding magic padding, margin, or color values.
* **Forms Validation:** Map inputs to React Hook Form. Validate properties using Zod schemas matching the backend's validation constraints.

---

## 3. Operations & Docker Standards

* **Config Isolation:** Never hardcode credentials, secrets, or endpoint domain names. Injected parameters must be declared inside `application.properties` (backend) or `.env` files (frontend), resolved from system environment variables.
* **Docker Compose:** Maintain service health checks inside the `docker-compose` configuration to ensure downstream services wait for database and indexer startup.

---

## 4. Testing Expectations

* **Unit Testing:** Implement unit tests for complex business calculations (e.g., variant SKU auto-generation and dynamic price calculations).
* **Validation Testing:** Ensure incorrect inputs are validated and caught before reaching service operations, returning proper `400 Bad Request` states with structured errors.
