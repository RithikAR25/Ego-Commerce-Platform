# RBAC Flow — EGO Platform

> **Model:** Role-Based Access Control (RBAC)
> **Roles:** CUSTOMER, ADMIN
> **Enforcement:** Route-level (SecurityConfig) + Method-level (@PreAuthorize)

---

## Role Model

```
UserRole enum:
  CUSTOMER  →  DB value: "customer"  →  Spring authority: "ROLE_CUSTOMER"
  ADMIN     →  DB value: "admin"     →  Spring authority: "ROLE_ADMIN"

Bridge method (UserRole.java):
  toGrantedAuthority() → new SimpleGrantedAuthority("ROLE_" + this.name())
```

### Role Stored in Two Places

| Location | Value | Purpose |
|----------|-------|---------|
| `users.role` (MySQL ENUM) | `"customer"` or `"admin"` | Source of truth in DB |
| JWT claim `"role"` | `"CUSTOMER"` or `"ADMIN"` | Embedded for stateless auth |
| `User.getAuthorities()` | `ROLE_CUSTOMER` / `ROLE_ADMIN` | Spring Security evaluates this |

---

## Route-Level Authorization (SecurityConfig)

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(PUBLIC_MATCHERS).permitAll()      // /auth/**, /docs, /swagger-ui
    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()  // CORS preflight
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")   // Admin-only
    .anyRequest().authenticated()                           // Everything else: any role
)
```

| Path Pattern | Access |
|---|---|
| `/api/v1/auth/**` | Public — no token required |
| `/docs`, `/v3/api-docs/**`, `/swagger-ui/**` | Public |
| `/actuator/health` | Public |
| `/api/v1/admin/**` | ADMIN only |
| Everything else | Any authenticated user (CUSTOMER or ADMIN) |

---

## Method-Level Authorization (@PreAuthorize)

Enabled via `@EnableMethodSecurity(prePostEnabled = true)` in SecurityConfig.

```java
// Restrict a service method to admins:
@PreAuthorize("hasRole('ADMIN')")
public void deleteProduct(Long productId) { ... }

// Restrict to the owner or admin:
@PreAuthorize("hasRole('ADMIN') or #email == authentication.name")
public UserResponse getUser(String email) { ... }

// Restrict to customers:
@PreAuthorize("hasRole('CUSTOMER')")
public void placeOrder(Long userId) { ... }
```

> **Important:** In `hasRole('X')` expressions, Spring automatically prepends `ROLE_`.
> So `hasRole('ADMIN')` matches the authority `ROLE_ADMIN`.

---

## RBAC Flow — Request Lifecycle

```
Request: DELETE /api/v1/admin/products/42
Header:  Authorization: Bearer eyJhbGci... (CUSTOMER token)

JwtAuthenticationFilter:
    → Extracts email from JWT
    → Loads User entity (role = CUSTOMER)
    → Sets SecurityContext: authorities = [ROLE_CUSTOMER]

AuthorizationFilter (route matcher):
    → Path matches /api/v1/admin/**
    → Required: ROLE_ADMIN
    → Current: ROLE_CUSTOMER
    → DENIED → calls JwtAccessDeniedHandler
    → Response: 403 Forbidden { "success": false, "message": "Access denied..." }
```

---

## Planned Permission Model (Future — Phase 11+)

For fine-grained admin permissions (e.g., "can manage orders" but "cannot manage users"), a permission system can be layered on top of RBAC:

```sql
-- Future tables:
permissions (id, name, description)            -- ORDER_MANAGE, PRODUCT_EDIT, USER_SUSPEND
role_permissions (role, permission_id)          -- ADMIN → all; OPERATOR → ORDER_MANAGE only
```

```java
// Future @PreAuthorize pattern:
@PreAuthorize("hasPermission(null, 'ORDER_MANAGE')")
public void updateOrderStatus(...) { ... }
```

Implementation via Spring Security's `PermissionEvaluator` interface.
This is additive — no changes to the current role model needed.

---

## Security Best Practices Applied

| Practice | Implementation |
|----------|---------------|
| Principle of Least Privilege | CUSTOMER cannot access /admin/** |
| Defense in Depth | Route-level AND method-level guards |
| No role elevation | Role stored in DB + JWT; never client-controlled |
| Fail-secure | Unknown paths default to `authenticated()` — never `permitAll()` |
| Audit trail | `audit_logs` table records all state changes (Phase 11) |
