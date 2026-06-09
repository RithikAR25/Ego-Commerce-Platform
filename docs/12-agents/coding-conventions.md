# coding-conventions.md — EGO Platform

> **Purpose:** Coding standards, architectural rules, naming conventions, and patterns that ALL code in this project must follow.  
> **Source:** Verified from `AGENT_CONTEXT/ARCHITECTURE_RULES.md`, `AGENT_CONTEXT/IMPLEMENTATION_RULES.md`, and direct source code inspection.  
> **Enforcement:** These are not suggestions — they are rules derived from production bugs fixed during development.

---

## 1. Backend — Critical Architecture Rules

### 1.1 Transaction Boundaries

**Rule:** External HTTP calls MUST NEVER be inside `@Transactional`.

```java
// ❌ WRONG — holds DB connection during Cloudinary upload (1–5 seconds)
@Transactional
public void uploadAndSave(MultipartFile file, Long productId) {
    String url = cloudinaryService.upload(file); // External HTTP — NEVER inside @Transactional
    productImageRepository.save(new ProductImage(url, productId));
}

// ✅ CORRECT — split into two calls
public void uploadAndSave(MultipartFile file, Long productId) {
    String url = cloudinaryService.upload(file);   // External HTTP — outside transaction
    imageService.persist(productId, url);          // @Transactional boundary starts here
}
```

Applies to: Cloudinary, Razorpay, SendGrid, any external SDK call.

### 1.2 `saveAndFlush()` vs `save()`

**Rule:** Use `saveAndFlush()` (not `save()`) when the DTO is built from the same entity in the same transaction.

```java
// ❌ WRONG — id, createdAt, updatedAt will be null in the response
Order saved = orderRepository.save(order);
return toDetailResponse(saved); // @Id / @CreationTimestamp not populated yet

// ✅ CORRECT — forces DB roundtrip, all generated values are populated
Order saved = orderRepository.saveAndFlush(order);
return toDetailResponse(saved);
```

### 1.3 `@Modifying` Queries

**Rule:** All bulk JPQL UPDATE queries MUST include `clearAutomatically = true`.

```java
// ❌ WRONG — Hibernate 1st-level cache returns stale entity version after bulk update
@Modifying
@Query("UPDATE InventoryRecord i SET i.quantityAvailable = ...")
int adjustQuantity(...);

// ✅ CORRECT — flushes and evicts cache after bulk UPDATE
@Modifying(clearAutomatically = true)
@Query("UPDATE InventoryRecord i SET i.quantityAvailable = ...")
int adjustQuantity(...);
```

### 1.4 Cross-Module Boundaries

**Rule:** Modules MUST NOT import each other's JPA entities. Communicate via service method calls only.

```java
// ❌ WRONG — review module importing order entity
import com.ego.raw_ego.order.entity.Order;

// ✅ CORRECT — use a JPQL query that joins across tables
@Query("SELECT COUNT(oi) > 0 FROM OrderItem oi JOIN oi.variant v " +
       "JOIN v.product p WHERE oi.order.user.id = :userId AND p.id = :productId ...")
boolean hasDeliveredOrderForProduct(Long userId, Long productId);
```

### 1.5 HtmlSanitizer

**Rule:** Apply `HtmlSanitizer.sanitize()` at the SERVICE LAYER on every user-supplied free-text field before persisting.

```java
// ✅ CORRECT — two-layer defence
// Layer 1: Bean Validation @Pattern on DTO (blocks HTML in constrained fields)
// Layer 2: HtmlSanitizer in service (strips HTML tags, event handlers, javascript: URIs)
.city(HtmlSanitizer.sanitize(request.getCity().trim(), 100))
.landmark(request.getLandmark() != null
    ? HtmlSanitizer.sanitize(request.getLandmark().trim(), 255) : null)
```

---

## 2. Backend — Naming Conventions

### Packages
```
com.ego.raw_ego.{module}/
    entity/          ← JPA entities
    repository/      ← Spring Data repositories
    service/         ← Business logic (annotated @Service)
    controller/      ← REST controllers (annotated @RestController)
    dto/
        request/     ← Request DTOs (annotated with @Valid constraints)
        response/    ← Response DTOs (static from() factory methods)
    enums/           ← Domain enums (no state — pure value objects)
```

### Classes
- Entity: `Order`, `Product`, `Category` (noun, no suffix)
- Service: `OrderService`, `CartService` (noun + `Service`)
- Controller: `OrderController` (noun + `Controller`)
- Repository: `OrderRepository` (noun + `Repository`)
- Request DTO: `CheckoutRequest`, `CreateProductRequest`
- Response DTO: `OrderDetailResponse`, `ProductSummaryResponse`
- Exception: `OrderNotFoundException`, `ReturnWindowExpiredException` (extends `EgoException`)

### Database
- Table names: `snake_case` (e.g. `order_items`, `product_variants`)
- FK columns: `{referenced_table_singular}_id` (e.g. `order_id`, `category_id`)
- Boolean columns: `is_{adjective}` (e.g. `is_active`, `is_deleted`, `is_primary`)
- Timestamp columns: `created_at`, `updated_at` (managed by `@CreationTimestamp`, `@UpdateTimestamp`)
- Soft delete: `is_deleted = true` (never hard-delete most entities)

---

## 3. Backend — Error Handling Patterns

### Exception Hierarchy
```
EgoException (base, carries HttpStatus)
├── AuthException (401)
├── ResourceNotFoundException (404)
├── ConflictException (409)
├── ReturnWindowExpiredException (409)
├── WebhookSignatureException (400)
└── ImageUploadException (500)
```

**Rule:** Never return error details from `GlobalExceptionHandler` — wrap in `ApiResponse<null>` with `success: false`.

### Validation Errors
```java
// Bean validation failure → GlobalExceptionHandler returns:
{
  "success": false,
  "errors": [
    { "field": "email", "message": "must be a valid email" },
    { "field": "password", "message": "must be at least 8 characters" }
  ]
}
```

---

## 4. Frontend — Conventions

### API Function Pattern
```typescript
// ✅ CORRECT — all API functions in src/api/{domain}.api.ts
// Use apiClient (Axios instance with JWT interceptor) — NEVER plain fetch
import { apiClient } from '@/api/client';

export const getOrders = async (page: number): Promise<OrderListResponse> => {
  const { data } = await apiClient.get<ApiResponse<OrderListResponse>>('/orders', { params: { page } });
  return data.data;
};
```

### TanStack Query Pattern
```typescript
// ✅ CORRECT — all queries in hooks/use{Feature}.ts
const { data: orders, isLoading } = useQuery({
  queryKey: orderKeys.list(page),
  queryFn: () => getOrders(page),
});

// ✅ CORRECT — mutations invalidate related query keys
const { mutate: checkout } = useMutation({
  mutationFn: checkoutApi,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: cartKeys.all() });
    cartStore.resetBadge();
  },
});
```

### TypeScript Rules
- ALL API response types must be defined in `src/types/{domain}.types.ts`
- ALL Axios calls return `ApiResponse<T>` — access `.data.data` for payload
- NO `any` types — use unknown + type guards if needed
- Use `zod` for all form validation schemas in `src/schemas/`

### State Management Rules
- **Server state** (anything from API) → TanStack Query. NEVER in Zustand.
- **Auth state** (user, access token) → Zustand `authStore` only
- **Cart badge** → Zustand `cartStore.itemCount` only
- **UI state** (modal open, tab index) → local `useState` in component
- **Form state** → React Hook Form (never in Zustand or TanStack)

---

## 5. Security Rules

### What requires HtmlSanitizer
- All user-supplied free-text address fields
- Review title and body (in `ReviewService`)
- Any future free-text user input that is stored in DB and rendered

### What is handled by Bean Validation only
- `fullName` (regex: `^[A-Za-z\\s.'-]+$` — HTML chars blocked)
- `phone` (regex: `^[+]?[0-9]{7,15}$`)
- Structured fields (email format, enum values, numeric ranges)

### JWT Security Checklist
- AT: 15-minute expiry, in-memory storage, never in localStorage
- RT: 30-day expiry, SHA-256 hashed before DB storage, rotated on every use
- `passwordChangedAt` guard: any AT with `issuedAt < passwordChangedAt` is rejected
- Family revocation: RT reuse (theft scenario) revokes all tokens in the family
- `JWT_SECRET` must be overridden to a unique 256-bit hex key in production

---

## 6. Code Review Checklist

Before any code change, verify:

**Backend:**
- [ ] No external HTTP call inside `@Transactional`
- [ ] `saveAndFlush()` used when DTO is built from same entity in same transaction
- [ ] `@Modifying(clearAutomatically = true)` on all bulk JPQL updates
- [ ] No cross-module entity imports — use service methods
- [ ] `HtmlSanitizer` applied to any new user-supplied free-text field
- [ ] New exception extends `EgoException` with appropriate `HttpStatus`
- [ ] `@Valid` on all request DTOs in controller parameters
- [ ] Public endpoint added to `SecurityConfig.PUBLIC_MATCHERS` if unauthenticated

**Frontend:**
- [ ] API function uses `apiClient` (not plain fetch or plain axios)
- [ ] Server data in TanStack Query, not Zustand
- [ ] TypeScript type defined in `types/{domain}.types.ts`
- [ ] Form uses React Hook Form + Zod schema
- [ ] Route added to `router/index.tsx` with appropriate guard
- [ ] `queryClient.invalidateQueries()` called after mutations that affect other queries
